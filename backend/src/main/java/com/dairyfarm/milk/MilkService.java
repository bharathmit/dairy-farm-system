package com.dairyfarm.milk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.contact.Contact;
import com.dairyfarm.contact.ContactRepository;
import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.contact.ContactService;
import com.dairyfarm.farm.Farm;
import com.dairyfarm.farm.FarmRepository;
import com.dairyfarm.farm.FarmService;
import com.dairyfarm.farm.FarmSettings;
import com.dairyfarm.milk.MilkGrader.Grade;
import com.dairyfarm.milk.MilkGrader.Quality;
import com.dairyfarm.milk.MilkRepository.CowInfo;
import com.dairyfarm.milk.MilkRepository.CowRow;
import com.dairyfarm.milk.MilkRepository.SaleRow;
import com.dairyfarm.milk.MilkRepository.Totals;
import com.dairyfarm.milk.MilkViews.AnimalRef;
import com.dairyfarm.milk.MilkViews.Change;
import com.dairyfarm.milk.MilkViews.CowDay;
import com.dairyfarm.milk.MilkViews.CowDayForm;
import com.dairyfarm.milk.MilkViews.CowDayRequest;
import com.dairyfarm.milk.MilkViews.DaySummary;
import com.dairyfarm.milk.MilkViews.ExpectationView;
import com.dairyfarm.milk.MilkViews.Product;
import com.dairyfarm.milk.MilkViews.Sale;
import com.dairyfarm.milk.MilkViews.SaleRequest;
import com.dairyfarm.milk.MilkViews.Session;
import com.dairyfarm.milk.MilkViews.SessionTotal;
import com.dairyfarm.milk.MilkViews.Sheet;
import com.dairyfarm.milk.MilkViews.SheetCow;
import com.dairyfarm.milk.MilkViews.SheetEntry;
import com.dairyfarm.milk.MilkViews.SheetRequest;
import com.dairyfarm.milk.MilkViews.TrendPoint;
import com.dairyfarm.milk.MilkViews.Yesterday;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.security.CurrentUser;

@Service
public class MilkService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final MilkRepository milk;
    private final FarmRepository farms;
    private final ContactService contactService;
    private final ContactRepository contacts;

    public MilkService(MilkRepository milk, FarmRepository farms, ContactService contactService, ContactRepository contacts) {
        this.milk = milk;
        this.farms = farms;
        this.contactService = contactService;
        this.contacts = contacts;
    }

    // ---- grading rules

    @Transactional(readOnly = true)
    public MilkGradingRules grading(Long farmId) {
        return farm(farmId).getMilkGrading();
    }

    @Transactional
    public MilkGradingRules updateGrading(Long farmId, MilkGradingRules req) {
        MilkGradingRules r = req.resolved();
        if (!(r.bandA() > r.bandB() && r.bandB() > r.bandC())) {
            throw ApiException.badRequest("Grade bands must go down: A above B above C.");
        }
        Set<String> names = new HashSet<>();
        for (var b : r.breeds()) {
            if (!names.add(b.breed().trim().toLowerCase())) {
                throw ApiException.badRequest("Breed \"" + b.breed().trim() + "\" is listed twice.");
            }
        }
        farm(farmId).setMilkGrading(r);
        return r;
    }

    // ---- home

    @Transactional(readOnly = true)
    public DaySummary day(Long farmId, LocalDate date) {
        MilkGradingRules rules = grading(farmId);
        List<CowInfo> milking = milk.milkingCows(farmId, null);
        List<CowRow> rows = milk.cowRows(farmId, date, date, null);
        Map<Long, CowInfo> cows = new LinkedHashMap<>();
        milking.forEach(c -> cows.put(c.id(), c));
        Set<Long> missing = rows.stream().map(CowRow::animalId).filter(id -> !cows.containsKey(id))
                .collect(Collectors.toSet());
        milk.animals(farmId, missing).forEach(c -> cows.put(c.id(), c));
        List<CowDay> days = cowDays(farmId, rules, cows, rows, date, date);

        Totals am = milk.cowTotals(farmId, date, Session.AM);
        Totals pm = milk.cowTotals(farmId, date, Session.PM);
        Totals yAm = milk.cowTotals(farmId, date.minusDays(1), Session.AM);
        Totals yPm = milk.cowTotals(farmId, date.minusDays(1), Session.PM);
        BigDecimal expected = days.stream()
                .filter(d -> cows.get(d.animalId()).milking() && d.expectedLitres() != null)
                .map(CowDay::expectedLitres).reduce(ZERO, BigDecimal::add);
        boolean todayFull = am.cows() > 0 && pm.cows() > 0;
        boolean yFull = yAm.cows() > 0 && yPm.cows() > 0;
        return new DaySummary(date,
                new SessionTotal(am.litres(), am.cows(), am.withheldLitres()),
                new SessionTotal(pm.litres(), pm.cows(), pm.withheldLitres()),
                am.litres().add(pm.litres()),
                new Yesterday(yAm.litres(), yPm.litres(), yAm.litres().add(yPm.litres())),
                new Change(pct(am, yAm), pct(pm, yPm),
                        todayFull && yFull ? change(am.litres().add(pm.litres()), yAm.litres().add(yPm.litres())) : null),
                expected, milking.size(), days);
    }

    @Transactional(readOnly = true)
    public List<TrendPoint> trend(Long farmId, LocalDate from, LocalDate to) {
        DailyDates.range(from, to, 366);
        return milk.trend(farmId, from, to);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> grades(Long farmId, LocalDate from, LocalDate to) {
        DailyDates.range(from, to, 366);
        List<CowRow> rows = milk.cowRows(farmId, from, to, null);
        Map<Long, CowInfo> cows = milk.animals(farmId, rows.stream().map(CowRow::animalId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CowInfo::id, Function.identity()));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Grade g : Grade.values()) {
            counts.put(g.name(), 0L);
        }
        cowDays(farmId, grading(farmId), cows, rows, from, to).stream()
                .filter(d -> d.grade() != null)
                .forEach(d -> counts.merge(d.grade().name(), 1L, Long::sum));
        return counts;
    }

    // ---- herd sheet

    @Transactional(readOnly = true)
    public Sheet sheet(Long farmId, LocalDate date, Session session, Long groupId) {
        session = sessionOnly(session);
        FarmSettings settings = farm(farmId).getSettings();
        List<CowInfo> cows = milk.milkingCows(farmId, groupId);
        List<Long> ids = cows.stream().map(CowInfo::id).toList();
        final Session s = session;
        Map<Long, BigDecimal> saved = milk.cowRows(farmId, date, date, null).stream()
                .filter(r -> r.session() == s && r.quantity() != null)
                .collect(Collectors.toMap(CowRow::animalId, CowRow::quantity));
        Map<Long, List<BigDecimal>> recent = milk.recent(farmId, ids, session, date);
        Map<Long, Instant> withdrawals = milk.withdrawals(farmId, ids, date, sessionTime(settings, date, session));
        List<SheetCow> list = cows.stream().map(c -> {
            List<BigDecimal> r = recent.getOrDefault(c.id(), List.of());
            BigDecimal usual = r.isEmpty() ? null
                    : r.stream().reduce(ZERO, BigDecimal::add).divide(BigDecimal.valueOf(r.size()), 1, RoundingMode.HALF_UP);
            Instant until = withdrawals.get(c.id());
            return new SheetCow(c.id(), c.tag(), c.name(), c.groupId(), saved.get(c.id()),
                    r.isEmpty() ? null : r.get(0), usual, until != null, until);
        }).toList();
        Totals here = milk.cowTotals(farmId, date, session);
        Totals other = milk.cowTotals(farmId, date, session == Session.AM ? Session.PM : Session.AM);
        return new Sheet(date, session, here.cows() > 0, here.litres(), other.litres(), here.litres().add(other.litres()),
                here.withheldLitres().add(other.withheldLitres()), list);
    }

    @Transactional
    public Sheet saveSheet(Long farmId, SheetRequest req) {
        Session session = sessionOnly(req.session());
        DailyDates.notFuture(req.date());
        Map<Long, SheetEntry> entries = new LinkedHashMap<>();
        req.entries().forEach(e -> entries.put(e.animalId(), e));
        Map<Long, CowInfo> cows = requireMilking(farmId, entries.keySet());
        var user = CurrentUser.idOrNull();
        for (SheetEntry e : entries.values()) {
            if (e.litres() == null) {
                milk.deleteCowSession(farmId, e.animalId(), req.date(), session);
            } else {
                milk.upsertCowSession(farmId, e.animalId(), req.date(), session, e.litres(), false,
                        null, null, null, null, null, null, user);
            }
        }
        refresh(farmId, cows.keySet(), req.date(), req.date());
        return sheet(farmId, req.date(), session, null);
    }

    // ---- one cow

    @Transactional(readOnly = true)
    public CowDayForm cowDay(Long farmId, Long animalId, LocalDate date) {
        CowInfo cow = milk.animal(farmId, animalId).orElseThrow(() -> ApiException.notFound("Animal not found."));
        MilkGradingRules rules = grading(farmId);
        FarmSettings settings = farm(farmId).getSettings();
        List<CowRow> rows = milk.cowRows(farmId, date, date, animalId);
        CowDay day = cowDays(farmId, rules, Map.of(cow.id(), cow), rows, date, date).stream().findFirst()
                .orElseGet(() -> empty(cow, date));
        BigDecimal peak = milk.ownPeaks(farmId, List.of(animalId)).get(animalId);
        var exp = MilkGrader.expect(rules, cow.breed(), cow.lastCalvingDate(), date, peak, firstCalver(cow));
        var lastAm = milk.recent(farmId, List.of(animalId), Session.AM, date).getOrDefault(animalId, List.of());
        var lastPm = milk.recent(farmId, List.of(animalId), Session.PM, date).getOrDefault(animalId, List.of());
        Instant until = milk.withdrawals(farmId, List.of(animalId), date, sessionTime(settings, date, Session.AM)).get(animalId);
        return new CowDayForm(new AnimalRef(cow.id(), cow.tag(), cow.name(), cow.breed(), cow.lastCalvingDate()),
                day, ExpectationView.of(exp), lastAm.isEmpty() ? null : lastAm.get(0),
                lastPm.isEmpty() ? null : lastPm.get(0), until != null, until);
    }

    @Transactional
    public CowDayForm saveCowDay(Long farmId, Long animalId, LocalDate date, CowDayRequest req) {
        DailyDates.notFuture(date);
        requireMilking(farmId, List.of(animalId));
        var user = CurrentUser.idOrNull();
        String notes = req.notes() == null || req.notes().isBlank() ? null : req.notes().trim();
        for (Session s : List.of(Session.AM, Session.PM)) {
            BigDecimal litres = s == Session.AM ? req.am() : req.pm();
            if (litres == null) {
                milk.deleteCowSession(farmId, animalId, date, s);
            } else {
                milk.upsertCowSession(farmId, animalId, date, s, litres, true, req.fatPct(), req.snfPct(),
                        req.proteinPct(), req.density(), req.scc(), notes, user);
            }
        }
        refresh(farmId, List.of(animalId), date, date);
        return cowDay(farmId, animalId, date);
    }

    @Transactional(readOnly = true)
    public List<CowDay> cowHistory(Long farmId, Long animalId, LocalDate from, LocalDate to) {
        DailyDates.range(from, to, 366);
        CowInfo cow = milk.animal(farmId, animalId).orElseThrow(() -> ApiException.notFound("Animal not found."));
        List<CowDay> days = new ArrayList<>(cowDays(farmId, grading(farmId), Map.of(cow.id(), cow),
                milk.cowRows(farmId, from, to, animalId), from, to));
        days.sort(Comparator.comparing(CowDay::date).reversed());
        return days;
    }

    // ---- farm totals / sales

    @Transactional(readOnly = true)
    public List<Sale> sales(Long farmId, LocalDate from, LocalDate to, boolean showMoney) {
        DailyDates.range(from, to, 366);
        List<SaleRow> rows = milk.sales(farmId, from, to);
        Map<Long, Contact> buyers = contacts.findByFarmIdAndIdIn(farmId,
                        rows.stream().map(SaleRow::buyerContactId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Contact::getId, Function.identity()));
        return rows.stream().map(r -> toSale(farmId, r, buyers.get(r.buyerContactId()), showMoney)).toList();
    }

    @Transactional
    public Sale saveSale(Long farmId, SaleRequest req, boolean showMoney) {
        DailyDates.notFuture(req.date());
        Product product = req.product() == null ? Product.MILK : req.product();
        Contact buyer = contactService.require(farmId, req.buyerContactId(), Contact.Role.BUYER);
        Totals cowTotals = product == Product.MILK ? milk.cowTotals(farmId, req.date(), req.session()) : null;
        BigDecimal quantity = req.quantity() != null ? req.quantity()
                : cowTotals != null && cowTotals.cows() > 0 ? cowTotals.litres() : null;
        BigDecimal sold = req.soldQty();
        if (sold != null && sold.signum() > 0) {
            if (quantity == null) {
                throw ApiException.badRequest("Enter the total litres, or log each cow first.");
            }
            BigDecimal withheld = cowTotals == null ? ZERO : cowTotals.withheldLitres();
            BigDecimal saleable = quantity.subtract(withheld).max(ZERO);
            if (sold.compareTo(saleable) > 0) {
                throw ApiException.badRequest("Only " + plain(saleable) + " L can be sold"
                        + (withheld.signum() > 0 ? ": " + plain(withheld) + " L is from cows in milk withdrawal." : "."));
            }
        }
        var existing = milk.sale(farmId, req.date(), req.session(), product);
        BigDecimal rate = req.rate();
        if (rate == null && !showMoney && existing.isPresent()) {
            rate = existing.get().rate(); // workers don't see the rate; keep what the owner set
        }
        if (rate == null && buyer != null) {
            rate = buyer.getMilkRate();
        }
        if (rate == null && product == Product.MILK) {
            rate = farm(farmId).getSettings().milkRate();
        }
        BigDecimal income = sold != null && rate != null ? sold.multiply(rate).setScale(2, RoundingMode.HALF_UP) : null;
        Long id = milk.upsertSale(farmId, req.date(), req.session(), product, req.quantity(), sold, rate, income,
                buyer == null ? null : buyer.getId(), req.fatPct(), req.snfPct(), req.paid(),
                req.notes() == null || req.notes().isBlank() ? null : req.notes().trim(), CurrentUser.idOrNull());
        return toSale(farmId, milk.sale(farmId, id).orElseThrow(), buyer, showMoney);
    }

    @Transactional
    public void deleteSale(Long farmId, Long id) {
        if (milk.deleteSale(farmId, id) == 0) {
            throw ApiException.notFound("Sale not found.");
        }
    }

    // ---- shared

    /** Re-marks withheld milk for these cows; also used by health records. */
    public void refresh(Long farmId, Collection<Long> animalIds, LocalDate from, LocalDate to) {
        FarmSettings s = farm(farmId).getSettings();
        milk.refreshWithheld(farmId, animalIds, from, to, s.morningMilking(), s.eveningMilking());
    }

    public static Instant sessionTime(FarmSettings settings, LocalDate date, Session session) {
        LocalTime t = session == Session.PM ? settings.eveningMilking() : settings.morningMilking();
        return date.atTime(t).atZone(FarmService.FARM_ZONE).toInstant();
    }

    private List<CowDay> cowDays(Long farmId, MilkGradingRules rules, Map<Long, CowInfo> cows, List<CowRow> rows,
                                 LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> peaks = milk.ownPeaks(farmId, cows.keySet());
        Map<String, CowRow[]> byDay = new LinkedHashMap<>();
        for (CowRow r : rows) {
            CowRow[] pair = byDay.computeIfAbsent(r.animalId() + "|" + r.date(), k -> new CowRow[2]);
            pair[r.session() == Session.AM ? 0 : 1] = r;
        }
        List<CowDay> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (CowRow[] pair : byDay.values()) {
            CowRow any = pair[0] != null ? pair[0] : pair[1];
            CowInfo cow = cows.get(any.animalId());
            if (cow == null) {
                continue;
            }
            seen.add(cow.id());
            out.add(build(rules, cow, any.date(), pair[0], pair[1], peaks.get(cow.id())));
        }
        if (from.equals(to)) {
            // single day: list every milking cow, logged or not
            for (CowInfo cow : cows.values()) {
                if (!seen.contains(cow.id()) && cow.milking()) {
                    out.add(build(rules, cow, from, null, null, peaks.get(cow.id())));
                }
            }
            Map<Long, Integer> order = new HashMap<>();
            int i = 0;
            for (Long id : cows.keySet()) {
                order.put(id, i++);
            }
            out.sort(Comparator.comparing((CowDay d) -> order.getOrDefault(d.animalId(), Integer.MAX_VALUE)));
        }
        return out;
    }

    private static CowDay build(MilkGradingRules rules, CowInfo cow, LocalDate date, CowRow am, CowRow pm, BigDecimal peak) {
        BigDecimal a = am == null ? null : am.quantity();
        BigDecimal p = pm == null ? null : pm.quantity();
        BigDecimal total = a == null && p == null ? null : (a == null ? ZERO : a).add(p == null ? ZERO : p);
        boolean withheld = (am != null && am.withheld()) || (pm != null && pm.withheld());
        var exp = MilkGrader.expect(rules, cow.breed(), cow.lastCalvingDate(), date, peak, firstCalver(cow));
        BigDecimal fat = pick(pm, am, CowRow::fatPct);
        BigDecimal snf = pick(pm, am, CowRow::snfPct);
        Integer scc = pm != null && pm.scc() != null ? pm.scc() : am == null ? null : am.scc();
        var result = a != null && p != null && !withheld
                ? MilkGrader.grade(rules, total, exp, new Quality(fat, snf, scc)) : null;
        return new CowDay(date, cow.id(), cow.tag(), cow.name(), a, p, total, withheld,
                exp == null ? null : exp.daysInMilk(),
                exp == null || exp.pastLactation() ? null : exp.expectedLitres(),
                result == null ? null : result.pctOfExpected(), result == null ? null : result.grade(),
                result == null ? List.of() : result.qualityIssues(),
                fat, snf, pick(pm, am, CowRow::proteinPct), pick(pm, am, CowRow::density), scc,
                pm != null && pm.notes() != null ? pm.notes() : am == null ? null : am.notes());
    }

    private CowDay empty(CowInfo cow, LocalDate date) {
        return new CowDay(date, cow.id(), cow.tag(), cow.name(), null, null, null, false, null, null, null, null,
                List.of(), null, null, null, null, null, null);
    }

    private static <T> T pick(CowRow first, CowRow second, Function<CowRow, T> f) {
        T v = first == null ? null : f.apply(first);
        return v != null ? v : second == null ? null : f.apply(second);
    }

    /** A first-calver is under 30 months old at her (last) calving. */
    static boolean firstCalver(CowInfo cow) {
        return cow.dateOfBirth() != null && cow.lastCalvingDate() != null
                && cow.dateOfBirth().plusMonths(30).isAfter(cow.lastCalvingDate());
    }

    private Map<Long, CowInfo> requireMilking(Long farmId, Collection<Long> ids) {
        Map<Long, CowInfo> found = milk.animals(farmId, ids).stream().collect(Collectors.toMap(CowInfo::id, Function.identity()));
        for (Long id : ids) {
            CowInfo c = found.get(id);
            if (c == null) {
                throw ApiException.badRequest("Animal " + id + " isn't on this farm.");
            }
            if (!c.milking()) {
                throw ApiException.badRequest(c.tag() + " isn't a milking cow. Change her category to Milking first.");
            }
        }
        return found;
    }

    private Sale toSale(Long farmId, SaleRow r, Contact buyer, boolean showMoney) {
        Totals t = r.product() == Product.MILK ? milk.cowTotals(farmId, r.date(), r.session()) : new Totals(ZERO, ZERO, 0);
        BigDecimal quantity = r.quantity() != null ? r.quantity() : t.cows() > 0 ? t.litres() : null;
        BigDecimal other = quantity == null ? null
                : quantity.subtract(r.soldQty() == null ? ZERO : r.soldQty()).max(ZERO);
        return new Sale(r.id(), r.date(), r.session(), r.product(), quantity, t.litres(), t.withheldLitres(),
                r.soldQty(), other, showMoney ? r.rate() : null, showMoney ? r.incomeAmount() : null,
                buyer == null ? null : Ref.of(buyer), r.fatPct(), r.snfPct(), r.paid(), r.notes());
    }

    private static Session sessionOnly(Session s) {
        if (s == Session.DAY) {
            throw ApiException.badRequest("Pick morning (AM) or evening (PM).");
        }
        return s;
    }

    private static BigDecimal pct(Totals today, Totals yesterday) {
        return today.cows() > 0 && yesterday.cows() > 0 ? change(today.litres(), yesterday.litres()) : null;
    }

    private static BigDecimal change(BigDecimal now, BigDecimal before) {
        if (before.signum() == 0) {
            return null;
        }
        return now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 1, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal v) {
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private Farm farm(Long farmId) {
        return farms.findById(farmId).orElseThrow(() -> ApiException.notFound("Farm not found."));
    }
}
