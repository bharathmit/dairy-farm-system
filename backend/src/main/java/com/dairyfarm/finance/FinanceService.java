package com.dairyfarm.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.breeding.BreedingViews.AnimalRef;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.common.PageResponse;
import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.farm.FarmRepository;
import com.dairyfarm.finance.FinanceRepository.Row;
import com.dairyfarm.finance.FinanceRepository.Write;
import com.dairyfarm.finance.FinanceViews.*;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.security.CurrentUser;

@Service
public class FinanceService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final FinanceRepository repo;
    private final FinancePoster poster;
    private final FarmRepository farms;
    private final JdbcClient jdbc;

    public FinanceService(FinanceRepository repo, FinancePoster poster, FarmRepository farms, JdbcClient jdbc) {
        this.repo = repo;
        this.poster = poster;
        this.farms = farms;
        this.jdbc = jdbc;
    }

    // ---- summary

    @Transactional(readOnly = true)
    public Summary summary(Long farmId, LocalDate from, LocalDate to) {
        LocalDate today = DailyDates.today();
        if (to == null) {
            to = today;
        }
        if (from == null) {
            from = to.withDayOfMonth(1);
        }
        requireRange(from, to);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate pFrom = from.minusDays(days);
        LocalDate pTo = from.minusDays(1);
        Map<Category, BigDecimal> cur = repo.byCategory(farmId, from, to);
        Map<Category, BigDecimal> prev = repo.byCategory(farmId, pFrom, pTo);
        BigDecimal income = sum(cur, Direction.INCOME);
        BigDecimal expense = sum(cur, Direction.EXPENSE);
        BigDecimal pIncome = sum(prev, Direction.INCOME);
        BigDecimal pExpense = sum(prev, Direction.EXPENSE);
        BigDecimal margin = income.signum() > 0 ? pct(income.subtract(expense), income) : null;
        int alert = farms.findById(farmId).map(f -> f.getSettings().marginAlertPct()).orElse(30);
        BigDecimal milk = cur.getOrDefault(Category.MILK_SALES, ZERO);
        BigDecimal feed = cur.getOrDefault(Category.FEED, ZERO);
        return new Summary(from, to, income, expense, income.subtract(expense), margin, alert,
                margin != null && margin.compareTo(BigDecimal.valueOf(alert + 5L)) < 0,
                new Totals(pIncome, pExpense, pIncome.subtract(pExpense)), change(income, pIncome),
                change(expense, pExpense), repo.pending(farmId), list(cur, Direction.INCOME),
                list(cur, Direction.EXPENSE), milk.signum() > 0 ? pct(feed, milk) : null);
    }

    // ---- entries

    @Transactional(readOnly = true)
    public PageResponse<Entry> entries(Long farmId, LocalDate from, LocalDate to, Direction direction, Category category,
                                       Status status, Long animalId, int page, int size) {
        size = Math.min(Math.max(size, 1), 200);
        page = Math.max(page, 0);
        StringBuilder where = new StringBuilder();
        Map<String, Object> p = new HashMap<>();
        if (from != null) {
            where.append(" and e.entry_date >= :from");
            p.put("from", from);
        }
        if (to != null) {
            where.append(" and e.entry_date <= :to");
            p.put("to", to);
        }
        if (direction != null) {
            where.append(" and e.direction = :dir");
            p.put("dir", direction.name());
        }
        if (category != null) {
            where.append(" and e.category = :cat");
            p.put("cat", category.name());
        }
        if (status != null) {
            where.append(" and e.status = :status");
            p.put("status", status.name());
        }
        if (animalId != null) {
            where.append(" and e.animal_id = :animal");
            p.put("animal", animalId);
        }
        long total = repo.count(farmId, where.toString(), p);
        List<Entry> items = repo.list(farmId, where.toString(), p, size, (long) page * size).stream()
                .map(FinanceService::view).toList();
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    public Entry get(Long farmId, Long id) {
        return view(load(farmId, id));
    }

    @Transactional
    public Entry create(Long farmId, EntryRequest req) {
        Long id = repo.insert(farmId, write(farmId, req, null), CurrentUser.idOrNull());
        return get(farmId, id);
    }

    @Transactional
    public Entry update(Long farmId, Long id, EntryRequest req) {
        Row r = load(farmId, id);
        if (r.auto()) {
            Status status = req.status() == null ? r.status() : req.status();
            var method = req.paymentMethod() == null ? r.paymentMethod() : req.paymentMethod();
            repo.updateAuto(id, status, method, blank(req.notes()));
            repo.writeBackStatus(r, status, method);
            poster.sync(farmId);
        } else {
            repo.update(id, write(farmId, req, r));
        }
        return get(farmId, id);
    }

    @Transactional
    public void delete(Long farmId, Long id) {
        Row r = load(farmId, id);
        if (r.auto()) {
            throw ApiException.conflict("This entry comes from " + label(r.sourceType())
                    + ". Delete it from its source record.");
        }
        repo.delete(id);
    }

    /** Validated fields for a manual entry. */
    Write write(Long farmId, EntryRequest req, Row existing) {
        if (req.category() == null) {
            throw ApiException.badRequest("Pick a category.");
        }
        if (req.amount() == null) {
            throw ApiException.badRequest("Enter the amount.");
        }
        if (req.date() == null) {
            throw ApiException.badRequest("Enter the date.");
        }
        Direction dir = req.category().direction();
        if (req.direction() != null && req.direction() != dir) {
            throw ApiException.badRequest(pretty(req.category()) + " is " + (dir == Direction.INCOME ? "an income" : "an expense")
                    + " category.");
        }
        DailyDates.notFuture(req.date());
        AllocKind kind = req.allocKind() == null ? AllocKind.FARM : req.allocKind();
        Long groupId = null;
        Long animalId = null;
        if (kind == AllocKind.GROUP) {
            groupId = requireOnFarm("animal_group", farmId, req.groupId(), "Pick a group on this farm.");
        } else if (kind == AllocKind.ANIMAL) {
            animalId = requireOnFarm("animal", farmId, req.animalId(), "Pick an animal on this farm.");
        }
        String counterparty = blank(req.counterparty());
        if (req.contactId() != null) {
            String name = jdbc.sql("select name from contact where id = :id and farm_id = :farm")
                    .param("id", req.contactId()).param("farm", farmId).query(String.class).optional()
                    .orElseThrow(() -> ApiException.badRequest("That contact isn't on this farm."));
            if (counterparty == null) {
                counterparty = name;
            }
        }
        if (req.memberId() != null) {
            requireOnFarm("farm_member", farmId, req.memberId(), "That team member isn't on this farm.");
        }
        if (req.periodFrom() != null && req.periodTo() != null && req.periodTo().isBefore(req.periodFrom())) {
            throw ApiException.badRequest("The period ends before it starts.");
        }
        return new Write(dir, req.category(), req.amount().setScale(2, RoundingMode.HALF_UP), req.date(), req.time(),
                req.status() == null ? Status.PAID : req.status(), req.paymentMethod(), counterparty, req.contactId(),
                req.memberId(), req.periodFrom(), req.periodTo(), existing != null && existing.advance(),
                existing == null ? null : existing.advanceDeducted(), kind, groupId, animalId, req.quantity(),
                blank(req.unit()), blank(req.notes()));
    }

    private Long requireOnFarm(String table, Long farmId, Long id, String message) {
        if (id == null) {
            throw ApiException.badRequest(message);
        }
        boolean ok = jdbc.sql("select count(*) from " + table + " where id = :id and farm_id = :farm")
                .param("id", id).param("farm", farmId).query(Long.class).single() > 0;
        if (!ok) {
            throw ApiException.badRequest(message);
        }
        return id;
    }

    Row load(Long farmId, Long id) {
        return repo.find(farmId, id).orElseThrow(() -> ApiException.notFound("Entry not found."));
    }

    static Entry view(Row r) {
        AnimalRef animal = r.animalId() == null ? null : new AnimalRef(r.animalId(), r.animalTag(), r.animalName());
        GroupRef group = r.groupId() == null ? null : new GroupRef(r.groupId(), r.groupName());
        Source source = source(r);
        return new Entry(r.id(), r.direction(), r.category(), r.amount(), r.date(), r.time(), r.status(),
                r.paymentMethod(), r.counterparty(), r.contactId() == null ? null : new Ref(r.contactId(), r.contactName()),
                r.memberId() == null ? null : new MemberRef(r.memberId(), r.memberName()), r.periodFrom(), r.periodTo(),
                r.advance(), new Alloc(r.allocKind(), group, animal), r.quantity(), r.unit(), source, r.auto(), r.notes());
    }

    /** Where the entry came from, as the page to open: sale, item, equipment, health record, service, animal, worker. */
    static Source source(Row r) {
        String part = r.sourcePart() == null || r.sourcePart().isEmpty() ? null : r.sourcePart();
        if (r.sourceType() == null) {
            if (r.memberId() != null && r.category() == Category.LABOUR) {
                String what = r.advance() ? "Advance" : "Wages";
                String period = r.periodFrom() == null ? "" : " · " + Dates.range(r.periodFrom(), r.periodTo());
                return new Source("LABOUR", r.memberId(), null, what + " · " + r.memberName() + period);
            }
            return null;
        }
        return switch (r.sourceType()) {
            case "MILK_SALE" -> new Source("PRODUCTION", r.sourceId(), part, r.description());
            case "ITEM_TXN" -> "SERVICE".equals(r.txnType())
                    ? new Source("EQUIPMENT_SERVICE", r.txnItemId(), part, r.description())
                    : new Source("ITEM_TXN", r.txnItemId(), part, r.description());
            default -> new Source(r.sourceType(), r.sourceId(), part, r.description());
        };
    }

    private static String label(String sourceType) {
        return switch (sourceType) {
            case "MILK_SALE" -> "a milk sale";
            case "ITEM_TXN" -> "a stock purchase or equipment service";
            case "HEALTH" -> "a health record";
            case "BREEDING" -> "a breeding record";
            case "ANIMAL" -> "an animal's purchase or sale price";
            default -> "another record";
        };
    }

    private static String pretty(Category c) {
        String s = c.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- profit & loss

    /** One animal's on-farm interval and money lines. */
    record AnimalCalc(PnlAnimal animal, String status, long days, BigDecimal litres, Map<LineKey, BigDecimal> lines) {
        BigDecimal net() {
            return lines.values().stream().reduce(ZERO, BigDecimal::add);
        }
    }

    @Transactional(readOnly = true)
    public Pnl pnl(Long farmId, PnlScope scope, LocalDate from, LocalDate to) {
        LocalDate[] range = range(farmId, from, to);
        Map<Category, BigDecimal> cats = repo.byCategory(farmId, range[0], range[1]);
        BigDecimal income = sum(cats, Direction.INCOME);
        BigDecimal expense = sum(cats, Direction.EXPENSE);
        List<CategoryAmount> byCat = new ArrayList<>(list(cats, Direction.INCOME));
        byCat.addAll(list(cats, Direction.EXPENSE));
        FarmPnl farm = new FarmPnl(income, expense, income.subtract(expense), byCat);
        List<RankedAnimal> ranked = new ArrayList<>();
        if (scope != PnlScope.FARM) {
            List<AnimalCalc> calcs = animalCalcs(farmId, range[0], range[1]);
            int rank = 1;
            for (AnimalCalc c : calcs) {
                ranked.add(new RankedAnimal(rank++, c.animal(), c.net(), note(c)));
            }
        }
        return new Pnl(range[0], range[1], farm, ranked);
    }

    @Transactional(readOnly = true)
    public AnimalPnl animalPnl(Long farmId, Long animalId, LocalDate from, LocalDate to) {
        requireOnFarm("animal", farmId, animalId, "Animal not found.");
        LocalDate[] range = range(farmId, from, to);
        List<AnimalCalc> calcs = animalCalcs(farmId, range[0], range[1]);
        for (int i = 0; i < calcs.size(); i++) {
            AnimalCalc c = calcs.get(i);
            if (!c.animal().id().equals(animalId)) {
                continue;
            }
            List<PnlLine> lines = new ArrayList<>();
            for (LineKey k : LineKey.values()) {
                BigDecimal v = c.lines().get(k);
                if (v != null && (v.signum() != 0 || k == LineKey.MILK || k == LineKey.FEED)) {
                    lines.add(new PnlLine(k, lineLabel(k, c.litres()), v));
                }
            }
            BigDecimal net = c.net();
            BigDecimal positive = c.lines().values().stream().filter(v -> v.signum() > 0).reduce(ZERO, BigDecimal::add);
            // Cost per litre counts running costs only (feed, health, breeding, other), not the price paid for her.
            BigDecimal costs = c.lines().entrySet().stream()
                    .filter(e -> e.getKey() != LineKey.PURCHASE && e.getValue().signum() < 0)
                    .map(Map.Entry::getValue).reduce(ZERO, BigDecimal::add).negate();
            return new AnimalPnl(c.animal(), range[0], range[1], c.days(), c.litres(), lines, net,
                    c.days() > 0 ? net.divide(BigDecimal.valueOf(c.days()), 2, RoundingMode.HALF_UP) : null,
                    c.litres().signum() > 0 ? costs.divide(c.litres(), 2, RoundingMode.HALF_UP) : null,
                    positive.signum() > 0 ? pct(net, positive) : null, i + 1, calcs.size());
        }
        throw ApiException.notFound("That animal wasn't on the farm in this period.");
    }

    private static String lineLabel(LineKey k, BigDecimal litres) {
        return switch (k) {
            case MILK -> "Milk income · " + litres.stripTrailingZeros().toPlainString() + " L";
            case FEED -> "Feed · allocated share";
            case HEALTH -> "Health & vet";
            case BREEDING -> "Breeding & semen";
            case PURCHASE -> "Purchase price";
            case SALE -> "Sale price";
            case OTHER -> "Other";
        };
    }

    private static String note(AnimalCalc c) {
        if ("SOLD".equals(c.status())) {
            return "sold";
        }
        if ("DEAD".equals(c.status())) {
            return "died";
        }
        if (c.litres().signum() == 0) {
            if ("CALF".equals(c.animal().category())) {
                return "no income yet";
            }
            if ("HEIFER".equals(c.animal().category())) {
                return "rearing cost";
            }
        }
        return null;
    }

    /** All animals on the farm at some point in [from, to], ranked by net (best first). */
    List<AnimalCalc> animalCalcs(Long farmId, LocalDate from, LocalDate to) {
        LocalDate today = DailyDates.today();
        record A(Long id, String tag, String name, String category, String breed, int lactation, LocalDate dob,
                 String status, Long groupId, LocalDate start, LocalDate end) {
        }
        List<A> animals = jdbc.sql("""
                select id, tag, name, category, breed, lactation_number, date_of_birth, status, group_id,
                       coalesce(acquisition_date, date_of_birth, created_at::date) as start_date, exit_date
                from animal where farm_id = :farm""").param("farm", farmId)
                .query((rs, n) -> new A(rs.getLong("id"), rs.getString("tag"), rs.getString("name"),
                        rs.getString("category"), rs.getString("breed"), rs.getInt("lactation_number"),
                        rs.getObject("date_of_birth", LocalDate.class), rs.getString("status"),
                        rs.getObject("group_id", Long.class), rs.getObject("start_date", LocalDate.class),
                        rs.getObject("exit_date", LocalDate.class))).list();
        Map<Long, Long> days = new HashMap<>();
        List<A> present = new ArrayList<>();
        for (A a : animals) {
            LocalDate s = a.start() == null || a.start().isBefore(from) ? from : a.start();
            LocalDate e = a.end() == null ? (to.isAfter(today) ? today : to) : (a.end().isAfter(to) ? to : a.end());
            long d = ChronoUnit.DAYS.between(s, e) + 1;
            if (d > 0) {
                days.put(a.id(), d);
                present.add(a);
            }
        }
        // milk: her saleable litres × the farm's average sale rate in the period
        Map<Long, BigDecimal> litres = new HashMap<>();
        jdbc.sql("""
                select animal_id, sum(quantity) as l from production_record
                where farm_id = :farm and animal_id is not null and not withheld and record_date between :from and :to
                group by animal_id""").param("farm", farmId).param("from", from).param("to", to)
                .query((rs, n) -> litres.put(rs.getLong("animal_id"), rs.getBigDecimal("l"))).list();
        BigDecimal rate = jdbc.sql("""
                select case when sum(sold_qty) > 0 then sum(income_amount) / sum(sold_qty) end from production_record
                where farm_id = :farm and animal_id is null and product = 'MILK' and sold_qty > 0
                  and income_amount is not null and record_date between :from and :to""")
                .param("farm", farmId).param("from", from).param("to", to).query(BigDecimal.class).optional().orElse(null);
        if (rate == null) {
            rate = farms.findById(farmId).map(f -> f.getSettings().milkRate()).orElse(null);
        }
        if (rate == null) {
            rate = ZERO;
        }
        // entries allocated to an animal, by category
        Map<Long, Map<Category, BigDecimal>> direct = new HashMap<>();
        jdbc.sql("""
                select animal_id, category, sum(amount) as total from finance_entry
                where farm_id = :farm and alloc_kind = 'ANIMAL' and animal_id is not null
                  and entry_date between :from and :to group by animal_id, category""")
                .param("farm", farmId).param("from", from).param("to", to)
                .query((rs, n) -> direct.computeIfAbsent(rs.getLong("animal_id"), k -> new EnumMap<>(Category.class))
                        .put(Category.valueOf(rs.getString("category")), rs.getBigDecimal("total"))).list();
        // shared feed: farm-wide by head-days of every animal; group feed by head-days of its animals
        BigDecimal farmFeed = jdbc.sql("""
                select coalesce(sum(amount), 0) from finance_entry where farm_id = :farm and category = 'FEED'
                  and alloc_kind = 'FARM' and entry_date between :from and :to""")
                .param("farm", farmId).param("from", from).param("to", to).query(BigDecimal.class).single();
        Map<Long, BigDecimal> groupFeed = new HashMap<>();
        jdbc.sql("""
                select group_id, sum(amount) as total from finance_entry where farm_id = :farm and category = 'FEED'
                  and alloc_kind = 'GROUP' and group_id is not null and entry_date between :from and :to group by group_id""")
                .param("farm", farmId).param("from", from).param("to", to)
                .query((rs, n) -> groupFeed.put(rs.getLong("group_id"), rs.getBigDecimal("total"))).list();
        long totalDays = days.values().stream().mapToLong(Long::longValue).sum();
        Map<Long, Long> groupDays = new HashMap<>();
        for (A a : present) {
            if (a.groupId() != null) {
                groupDays.merge(a.groupId(), days.get(a.id()), Long::sum);
            }
        }
        Map<Long, BigDecimal> semen = new HashMap<>();
        jdbc.sql("""
                select dam_id, sum(semen_cost) as total from breeding_record where farm_id = :farm and semen_cost > 0
                  and service_date between :from and :to group by dam_id""")
                .param("farm", farmId).param("from", from).param("to", to)
                .query((rs, n) -> semen.put(rs.getLong("dam_id"), rs.getBigDecimal("total"))).list();

        List<AnimalCalc> out = new ArrayList<>();
        for (A a : present) {
            long d = days.get(a.id());
            Map<Category, BigDecimal> mine = direct.getOrDefault(a.id(), Map.of());
            Map<LineKey, BigDecimal> lines = new LinkedHashMap<>();
            BigDecimal l = litres.getOrDefault(a.id(), ZERO);
            lines.put(LineKey.MILK, money(l.multiply(rate)));
            BigDecimal feed = mine.getOrDefault(Category.FEED, ZERO);
            if (totalDays > 0) {
                feed = feed.add(share(farmFeed, d, totalDays));
            }
            if (a.groupId() != null && groupFeed.containsKey(a.groupId())) {
                feed = feed.add(share(groupFeed.get(a.groupId()), d, groupDays.get(a.groupId())));
            }
            lines.put(LineKey.FEED, money(feed).negate());
            lines.put(LineKey.HEALTH, money(mine.getOrDefault(Category.VET, ZERO)).negate());
            BigDecimal breeding = mine.getOrDefault(Category.BREEDING, ZERO).add(mine.getOrDefault(Category.SEMEN, ZERO))
                    .add(semen.getOrDefault(a.id(), ZERO));
            lines.put(LineKey.BREEDING, money(breeding).negate());
            lines.put(LineKey.PURCHASE, money(mine.getOrDefault(Category.ANIMAL_PURCHASE, ZERO)).negate());
            lines.put(LineKey.SALE, money(mine.getOrDefault(Category.ANIMAL_SALES, ZERO)));
            BigDecimal other = ZERO;
            for (var e : mine.entrySet()) {
                switch (e.getKey()) {
                    case FEED, VET, BREEDING, SEMEN, ANIMAL_PURCHASE, ANIMAL_SALES -> { }
                    default -> other = e.getKey().direction() == Direction.INCOME ? other.add(e.getValue())
                            : other.subtract(e.getValue());
                }
            }
            lines.put(LineKey.OTHER, money(other));
            Long age = a.dob() == null ? null : ChronoUnit.DAYS.between(a.dob(), today);
            out.add(new AnimalCalc(new PnlAnimal(a.id(), a.tag(), a.name(), a.category(), a.breed(), a.lactation(), age),
                    a.status(), d, l, lines));
        }
        out.sort(Comparator.comparing(AnimalCalc::net).reversed().thenComparing(c -> c.animal().tag()));
        return out;
    }

    private LocalDate[] range(Long farmId, LocalDate from, LocalDate to) {
        LocalDate today = DailyDates.today();
        if (to == null) {
            to = today;
        }
        if (from == null) {
            LocalDate first = jdbc.sql("""
                    select least((select min(coalesce(acquisition_date, date_of_birth, created_at::date)) from animal
                                  where farm_id = :farm),
                                 (select min(entry_date) from finance_entry where farm_id = :farm))""")
                    .param("farm", farmId).query(LocalDate.class).optional().orElse(null);
            from = first == null || first.isAfter(to) ? to : first;
        }
        requireRange(from, to);
        return new LocalDate[] {from, to};
    }

    @Transactional(readOnly = true)
    public List<DailyMoney> daily(Long farmId, LocalDate from, LocalDate to) {
        DailyDates.range(from, to, 366);
        return repo.daily(farmId, from, to);
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("The period ends before it starts.");
        }
    }

    // ---- helpers

    private static BigDecimal share(BigDecimal total, long part, long whole) {
        return whole <= 0 ? ZERO : total.multiply(BigDecimal.valueOf(part)).divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(Map<Category, BigDecimal> m, Direction d) {
        return m.entrySet().stream().filter(e -> e.getKey().direction() == d).map(Map.Entry::getValue)
                .reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<CategoryAmount> list(Map<Category, BigDecimal> m, Direction d) {
        return m.entrySet().stream().filter(e -> e.getKey().direction() == d)
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue().reversed())
                .map(e -> new CategoryAmount(e.getKey(), e.getValue())).toList();
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal change(BigDecimal cur, BigDecimal prev) {
        return prev.signum() > 0 ? pct(cur.subtract(prev), prev) : null;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
