package com.dairyfarm.report;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.alert.AlertRuleBook;
import com.dairyfarm.alert.AlertService;
import com.dairyfarm.alert.AlertViews.Alert;
import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.breeding.BreedingQueryService;
import com.dairyfarm.breeding.BreedingSettings;
import com.dairyfarm.breeding.BreedingViews;
import com.dairyfarm.breeding.BreedingViews.CowStatus;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.farm.Farm;
import com.dairyfarm.farm.FarmRepository;
import com.dairyfarm.finance.FinanceService;
import com.dairyfarm.finance.FinanceViews;
import com.dairyfarm.finance.FinanceViews.PnlScope;
import com.dairyfarm.milk.MilkGrader;
import com.dairyfarm.milk.MilkGradingRules;
import com.dairyfarm.milk.MilkRepository;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.report.ReportViews.Column;
import com.dairyfarm.report.ReportViews.Compare;
import com.dairyfarm.report.ReportViews.CowCard;
import com.dairyfarm.report.ReportViews.CullAdvisor;
import com.dairyfarm.report.ReportViews.CullAnimal;
import com.dairyfarm.report.ReportViews.CullCounts;
import com.dairyfarm.report.ReportViews.CullRow;
import com.dairyfarm.report.ReportViews.HealthTally;
import com.dairyfarm.report.ReportViews.Period;
import com.dairyfarm.report.ReportViews.Report;
import com.dairyfarm.report.ReportViews.ReportType;
import com.dairyfarm.report.ReportViews.Summary;
import com.dairyfarm.report.ReportViews.Verdict;
import com.dairyfarm.report.ReportViews.VerdictCounts;

/** Reports (JSON and CSV), the cull advisor and compare cows. Owner only. */
@Service
public class ReportService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Alerts that point to a production or health problem (not routine doses or reminders). */
    private static final java.util.Set<String> ISSUES = java.util.Set.of("YIELD_DROP", "HIGH_SCC", "LOW_FAT", "BCS_RANGE",
            "WITHDRAWAL", "HEALTH_FOLLOW_UP");

    private final JdbcClient jdbc;
    private final FinanceService finance;
    private final BreedingQueryService breeding;
    private final AlertService alerts;
    private final AlertRuleBook rules;
    private final FarmRepository farms;
    private final MilkRepository milk;

    public ReportService(JdbcClient jdbc, FinanceService finance, BreedingQueryService breeding, AlertService alerts,
                         AlertRuleBook rules, FarmRepository farms, MilkRepository milk) {
        this.jdbc = jdbc;
        this.finance = finance;
        this.breeding = breeding;
        this.alerts = alerts;
        this.rules = rules;
        this.farms = farms;
        this.milk = milk;
    }

    // ---------- summary ----------

    @Transactional
    public Summary summary(Long farmId, LocalDate from, LocalDate to) {
        LocalDate[] p = period(from, to);
        BigDecimal litres = milkLitres(farmId, p[0], p[1]);
        long days = ChronoUnit.DAYS.between(p[0], p[1]) + 1;
        BigDecimal prev = milkLitres(farmId, p[0].minusDays(days), p[0].minusDays(1));
        BigDecimal change = prev.signum() == 0 ? null
                : litres.subtract(prev).multiply(HUNDRED).divide(prev, 1, RoundingMode.HALF_UP);
        FinanceViews.Summary f = finance.summary(farmId, p[0], p[1]);
        BigDecimal feed = f.expenseByCategory().stream().filter(c -> c.category().name().equals("FEED"))
                .map(FinanceViews.CategoryAmount::amount).findFirst().orElse(BigDecimal.ZERO);
        BigDecimal perLitre = litres.signum() == 0 ? null : feed.divide(litres, 2, RoundingMode.HALF_UP);
        CullAdvisor c = cullAdvisor(farmId);
        return new Summary(p[0], p[1], litres, change, f.net(), f.income(), perLitre,
                new CullCounts(c.counts().cull(), c.counts().monitor()));
    }

    private BigDecimal milkLitres(Long farmId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                select coalesce(sum(quantity), 0) from production_record
                where farm_id = :f and animal_id is not null and record_date between :a and :b""")
                .param("f", farmId).param("a", from).param("b", to).query(BigDecimal.class).single();
    }

    private static LocalDate[] period(LocalDate from, LocalDate to) {
        LocalDate today = DailyDates.today();
        LocalDate t = to != null ? to : today;
        LocalDate f = from != null ? from : t.withDayOfMonth(1);
        if (f.isAfter(t)) {
            throw ApiException.badRequest("The start date must be on or before the end date.");
        }
        if (ChronoUnit.DAYS.between(f, t) > 3660) {
            throw ApiException.badRequest("Pick a period of up to 10 years.");
        }
        return new LocalDate[] {f, t};
    }

    // ---------- reports ----------

    @Transactional
    public Report report(Long farmId, ReportType type, LocalDate from, LocalDate to) {
        LocalDate[] p = period(from, to);
        Period period = new Period(p[0], p[1]);
        return switch (type) {
            case MILK -> milkReport(farmId, period);
            case COWS -> cowsReport(farmId, period);
            case BREEDING -> breedingReport(farmId, period);
            case FEED -> feedReport(farmId, period);
            case HEALTH -> healthReport(farmId, period);
            case PNL -> pnlReport(farmId, period);
        };
    }

    public String csv(Report r) {
        StringWriter out = new StringWriter();
        try (CSVPrinter csv = new CSVPrinter(out, CSVFormat.DEFAULT)) {
            csv.printRecord(r.columns().stream().map(Column::label).toList());
            for (Map<String, Object> row : r.rows()) {
                csv.printRecord(r.columns().stream().map(c -> cell(row.get(c.key()))).toList());
            }
            if (r.totals() != null && !r.totals().isEmpty()) {
                csv.println();
                for (Map.Entry<String, Object> e : r.totals().entrySet()) {
                    csv.printRecord(label(e.getKey()), cell(e.getValue()));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toString();
    }

    private static String cell(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal b) {
            return b.stripTrailingZeros().scale() < 0 ? b.setScale(0, RoundingMode.HALF_UP).toPlainString()
                    : b.stripTrailingZeros().toPlainString();
        }
        return v.toString();
    }

    /** "conceptionPct" → "Conception %" for the CSV totals block. */
    private static String label(String key) {
        String s = key.replaceAll("Pct$", " %").replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private Report milkReport(Long farmId, Period p) {
        List<Map<String, Object>> rows = jdbc.sql("""
                select a.tag, a.name, count(distinct pr.record_date) as days,
                       sum(pr.quantity) filter (where pr.session = 'AM') as am, sum(pr.quantity) filter (where pr.session = 'PM') as pm,
                       sum(pr.quantity) as total, sum(pr.quantity) filter (where pr.withheld) as withheld,
                       round(avg(pr.fat_pct), 2) as fat, round(avg(pr.snf_pct), 2) as snf
                from production_record pr join animal a on a.id = pr.animal_id
                where pr.farm_id = :f and pr.animal_id is not null and pr.record_date between :a and :b
                group by a.id, a.tag, a.name order by a.tag""")
                .param("f", farmId).param("a", p.from()).param("b", p.to())
                .query((rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("tag", rs.getString("tag"));
                    m.put("name", rs.getString("name"));
                    m.put("days", rs.getInt("days"));
                    m.put("amLitres", z(rs.getBigDecimal("am")));
                    m.put("pmLitres", z(rs.getBigDecimal("pm")));
                    m.put("totalLitres", z(rs.getBigDecimal("total")));
                    m.put("avgPerDay", rs.getInt("days") == 0 ? null
                            : z(rs.getBigDecimal("total")).divide(BigDecimal.valueOf(rs.getInt("days")), 1, RoundingMode.HALF_UP));
                    m.put("withheldLitres", z(rs.getBigDecimal("withheld")));
                    m.put("fatPct", rs.getBigDecimal("fat"));
                    m.put("snfPct", rs.getBigDecimal("snf"));
                    return m;
                }).list();
        BigDecimal am = sum(rows, "amLitres"), pm = sum(rows, "pmLitres"), total = sum(rows, "totalLitres");
        long days = jdbc.sql("""
                select count(distinct record_date) from production_record
                where farm_id = :f and animal_id is not null and record_date between :a and :b""")
                .param("f", farmId).param("a", p.from()).param("b", p.to()).query(Long.class).single();
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("amLitres", am);
        totals.put("pmLitres", pm);
        totals.put("totalLitres", total);
        totals.put("withheldLitres", sum(rows, "withheldLitres"));
        totals.put("days", days);
        totals.put("farmAvgPerDay", days == 0 ? null : total.divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP));
        return new Report("milk", "Milk production", p, List.of(col("tag", "Tag"), col("name", "Name"), col("days", "Days"),
                col("amLitres", "AM L"), col("pmLitres", "PM L"), col("totalLitres", "Total L"), col("avgPerDay", "L/day"),
                col("withheldLitres", "Withheld L"), col("fatPct", "Fat %"), col("snfPct", "SNF %")), rows, totals);
    }

    private Report cowsReport(Long farmId, Period p) {
        LocalDate today = DailyDates.today();
        Map<Long, BigDecimal> lifetime = nets(farmId, null, null);
        List<Map<String, Object>> rows = jdbc.sql("""
                select a.id, a.tag, a.name, a.breed, a.category, a.lactation_number, a.last_calving_date,
                       (select sum(pr.quantity) from production_record pr where pr.animal_id = a.id
                          and pr.record_date between :a and :b) as litres,
                       (select count(distinct pr.record_date) from production_record pr where pr.animal_id = a.id
                          and pr.record_date between :a and :b) as days
                from animal a where a.farm_id = :f and a.status = 'ACTIVE' and a.sex = 'FEMALE'
                  and a.category in ('MILKING', 'DRY') order by a.tag""")
                .param("f", farmId).param("a", p.from()).param("b", p.to())
                .query((rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    LocalDate calved = rs.getObject("last_calving_date", LocalDate.class);
                    BigDecimal litres = z(rs.getBigDecimal("litres"));
                    int days = rs.getInt("days");
                    m.put("tag", rs.getString("tag"));
                    m.put("name", rs.getString("name"));
                    m.put("breed", rs.getString("breed"));
                    m.put("category", rs.getString("category"));
                    m.put("lactation", rs.getInt("lactation_number"));
                    m.put("daysInMilk", calved == null || "DRY".equals(rs.getString("category")) ? null
                            : ChronoUnit.DAYS.between(calved, today) + 1);
                    m.put("litres", litres);
                    m.put("avgPerDay", days == 0 ? null : litres.divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP));
                    m.put("lifetimeNet", lifetime.get(rs.getLong("id")));
                    return m;
                }).list();
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("cows", rows.size());
        totals.put("litres", sum(rows, "litres"));
        return new Report("cows", "Cow performance", p, List.of(col("tag", "Tag"), col("name", "Name"), col("breed", "Breed"),
                col("category", "Status"), col("lactation", "Lactation"), col("daysInMilk", "DIM"), col("litres", "Litres"),
                col("avgPerDay", "L/day"), col("lifetimeNet", "Lifetime net ₹")), rows, totals);
    }

    private Report breedingReport(Long farmId, Period p) {
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.sql("""
                select e.event_date as d, a.tag, a.name, 'Heat' as ev, lower(coalesce(e.sub_type, '')) as detail
                  from farm_event e join animal a on a.id = e.animal_id
                  where e.farm_id = :f and e.event_type = 'HEAT' and e.event_date between :a and :b
                union all
                select r.service_date, a.tag, a.name, 'Service', r.method ||
                       coalesce(' · ' || i.name, '') || coalesce(' · ' || r.sire_tag, '')
                  from breeding_record r join animal a on a.id = r.dam_id left join item i on i.id = r.semen_item_id
                  where r.farm_id = :f and r.service_date between :a and :b
                union all
                select r.preg_check_date, a.tag, a.name, 'Pregnancy check',
                       case r.pregnancy_status when 'PREGNANT' then 'pregnant' when 'NOT_PREGNANT' then 'not pregnant' else '' end
                  from breeding_record r join animal a on a.id = r.dam_id
                  where r.farm_id = :f and r.preg_check_date between :a and :b
                union all
                select r.calving_date, a.tag, a.name, 'Calving', lower(coalesce(r.calving_outcome, '')) ||
                       coalesce(' · ' || lower(r.calving_ease), '')
                  from breeding_record r join animal a on a.id = r.dam_id
                  where r.farm_id = :f and r.calving_date between :a and :b
                order by 1, 2""")
                .param("f", farmId).param("a", p.from()).param("b", p.to())
                .query((rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", rs.getObject(1, LocalDate.class));
                    m.put("tag", rs.getString(2));
                    m.put("name", rs.getString(3));
                    m.put("event", rs.getString(4));
                    m.put("detail", rs.getString(5));
                    return rows.add(m);
                }).list();
        long heats = rows.stream().filter(r -> "Heat".equals(r.get("event"))).count();
        long services = rows.stream().filter(r -> "Service".equals(r.get("event"))).count();
        long pos = rows.stream().filter(r -> "Pregnancy check".equals(r.get("event")) && "pregnant".equals(r.get("detail"))).count();
        long neg = rows.stream().filter(r -> "Pregnancy check".equals(r.get("event")) && "not pregnant".equals(r.get("detail"))).count();
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("heats", heats);
        totals.put("services", services);
        totals.put("pdPositive", pos);
        totals.put("pdNegative", neg);
        totals.put("conceptionPct", pos + neg == 0 ? null
                : BigDecimal.valueOf(pos * 100).divide(BigDecimal.valueOf(pos + neg), 1, RoundingMode.HALF_UP));
        totals.put("calvings", rows.stream().filter(r -> "Calving".equals(r.get("event"))).count());
        return new Report("breeding", "Breeding", p, List.of(col("date", "Date"), col("tag", "Tag"), col("name", "Name"),
                col("event", "Event"), col("detail", "Detail")), rows, totals);
    }

    private Report feedReport(Long farmId, Period p) {
        List<Map<String, Object>> rows = jdbc.sql("""
                select i.name, i.category, i.unit,
                  coalesce(sum(t.quantity) filter (where t.txn_type = 'USAGE' and t.txn_date between :a and :b), 0) as used,
                  coalesce(sum(t.quantity) filter (where t.txn_type = 'PURCHASE' and t.txn_date between :a and :b), 0) as bought,
                  coalesce(sum(t.quantity) filter (where t.txn_type = 'PRODUCE' and t.txn_date between :a and :b), 0) as produced,
                  coalesce(sum(t.cost) filter (where t.txn_type = 'PURCHASE' and t.txn_date between :a and :b), 0) as cost,
                  coalesce(sum(case when t.txn_type = 'USAGE' then -t.quantity else t.quantity end), 0) as stock
                from item i left join item_txn t on t.item_id = i.id
                where i.farm_id = :f and i.item_type = 'FEED'
                group by i.id, i.name, i.category, i.unit order by i.category, i.name""")
                .param("f", farmId).param("a", p.from()).param("b", p.to())
                .query((rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("item", rs.getString("name"));
                    m.put("category", rs.getString("category"));
                    m.put("unit", rs.getString("unit"));
                    m.put("usedQty", rs.getBigDecimal("used"));
                    m.put("boughtQty", rs.getBigDecimal("bought"));
                    m.put("producedQty", rs.getBigDecimal("produced"));
                    m.put("purchaseCost", rs.getBigDecimal("cost"));
                    m.put("stockNow", rs.getBigDecimal("stock"));
                    return m;
                }).list();
        BigDecimal litres = milkLitres(farmId, p.from(), p.to());
        BigDecimal feedExpense = finance.summary(farmId, p.from(), p.to()).expenseByCategory().stream()
                .filter(c -> c.category().name().equals("FEED")).map(FinanceViews.CategoryAmount::amount).findFirst()
                .orElse(BigDecimal.ZERO);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("usedQty", sum(rows, "usedQty"));
        totals.put("purchaseCost", sum(rows, "purchaseCost"));
        totals.put("feedExpense", feedExpense);
        totals.put("milkLitres", litres);
        totals.put("feedCostPerLitre", litres.signum() == 0 ? null : feedExpense.divide(litres, 2, RoundingMode.HALF_UP));
        return new Report("feed", "Feed consumption", p, List.of(col("item", "Item"), col("category", "Category"),
                col("unit", "Unit"), col("usedQty", "Used"), col("boughtQty", "Bought"), col("producedQty", "Made / harvested"),
                col("purchaseCost", "Purchase ₹"), col("stockNow", "Stock now")), rows, totals);
    }

    private Report healthReport(Long farmId, Period p) {
        List<Map<String, Object>> rows = jdbc.sql("""
                select e.event_date, a.tag, a.name, e.sub_type, e.details->>'diagnosis' as dg, e.details->>'medicine' as md,
                       e.cost, c.name as vet
                from farm_event e join animal a on a.id = e.animal_id left join contact c on c.id = e.contact_id
                where e.farm_id = :f and e.event_type = 'HEALTH' and e.event_date between :a and :b
                order by e.event_date, a.tag""")
                .param("f", farmId).param("a", p.from()).param("b", p.to())
                .query((rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", rs.getObject(1, LocalDate.class));
                    m.put("tag", rs.getString(2));
                    m.put("name", rs.getString(3));
                    m.put("type", cap(rs.getString(4)));
                    m.put("diagnosis", rs.getString(5));
                    m.put("medicine", rs.getString(6));
                    m.put("vet", rs.getString(8));
                    m.put("cost", rs.getBigDecimal(7));
                    return m;
                }).list();
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("records", rows.size());
        totals.put("cost", sum(rows, "cost"));
        return new Report("health", "Health records", p, List.of(col("date", "Date"), col("tag", "Tag"), col("name", "Name"),
                col("type", "Type"), col("diagnosis", "Diagnosis"), col("medicine", "Medicine"), col("vet", "Vet"),
                col("cost", "Cost ₹")), rows, totals);
    }

    private Report pnlReport(Long farmId, Period p) {
        FinanceViews.Summary s = finance.summary(farmId, p.from(), p.to());
        List<Map<String, Object>> rows = new ArrayList<>();
        s.incomeByCategory().forEach(c -> rows.add(pnlRow("Income", c)));
        s.expenseByCategory().forEach(c -> rows.add(pnlRow("Expense", c)));
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("income", s.income());
        totals.put("expense", s.expense());
        totals.put("net", s.net());
        totals.put("marginPct", s.marginPct());
        return new Report("pnl", "Profit & loss", p, List.of(col("direction", "Type"), col("category", "Category"),
                col("amount", "Amount ₹")), rows, totals);
    }

    private static Map<String, Object> pnlRow(String dir, FinanceViews.CategoryAmount c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("direction", dir);
        m.put("category", cap(c.category().name()));
        m.put("amount", c.amount());
        return m;
    }

    // ---------- cull advisor ----------

    @Transactional
    public CullAdvisor cullAdvisor(Long farmId) {
        LocalDate today = DailyDates.today();
        Farm farm = farms.findById(farmId).orElseThrow(() -> ApiException.notFound("Farm not found."));
        BreedingSettings bs = farm.getBreedingSettings().resolved();
        MilkGradingRules grading = farm.getMilkGrading();
        var rb = rules.resolve(farm);
        int replaceYears = rb.get("REPLACEMENT_AGE").i("ageYears");
        BigDecimal maxScc = rb.get("HIGH_SCC").p("maxSccK");
        BigDecimal bcsMin = rb.get("BCS_RANGE").p("min"), bcsMax = rb.get("BCS_RANGE").p("max");

        List<Cow> cows = cows(farmId);
        if (cows.isEmpty()) {
            return new CullAdvisor(new VerdictCounts(0, 0, 0, 0), List.of());
        }
        List<Long> ids = cows.stream().map(Cow::id).toList();
        Map<Long, CowStatus> status = new HashMap<>();
        breeding.allStatuses(farmId, true).forEach(s -> status.put(s.animal().id(), s));
        Map<Long, BigDecimal[]> yield = new HashMap<>();
        jdbc.sql("""
                select animal_id, sum(quantity), count(distinct record_date) from production_record
                where farm_id = :f and animal_id is not null and record_date between :a and :b group by animal_id""")
                .param("f", farmId).param("a", today.minusDays(89)).param("b", today)
                .query((rs, i) -> yield.put(rs.getLong(1), new BigDecimal[] {rs.getBigDecimal(2), BigDecimal.valueOf(rs.getLong(3))}))
                .list();
        Map<Long, int[]> treat = new HashMap<>();
        Map<Long, BigDecimal> treatCost = new HashMap<>();
        jdbc.sql("""
                select animal_id, count(*) filter (where sub_type in ('ILLNESS', 'TREATMENT', 'INJURY')), coalesce(sum(cost), 0)
                from farm_event where farm_id = :f and event_type = 'HEALTH' and event_date >= :a group by animal_id""")
                .param("f", farmId).param("a", today.minusDays(89))
                .query((rs, i) -> {
                    treat.put(rs.getLong(1), new int[] {rs.getInt(2)});
                    treatCost.put(rs.getLong(1), rs.getBigDecimal(3));
                    return 0;
                }).list();
        Map<Long, BigDecimal> scc = latest(farmId, "scc", today.minusDays(30));
        Map<Long, BigDecimal> bcs = new HashMap<>();
        jdbc.sql("""
                select distinct on (animal_id) animal_id, bcs from farm_event
                where farm_id = :f and event_type = 'MEASUREMENT' and bcs is not null and event_date >= :a
                order by animal_id, event_date desc, id desc""")
                .param("f", farmId).param("a", today.minusDays(90))
                .query((rs, i) -> bcs.put(rs.getLong(1), rs.getBigDecimal(2))).list();
        Map<Long, BigDecimal> peaks = milk.ownPeaks(farmId, ids);
        Map<Long, List<Alert>> open = new HashMap<>();
        for (Alert a : alerts.visibleOpen(farmId, true)) {
            if (a.animal() != null && a.ruleCode() != null && ISSUES.contains(a.ruleCode())) {
                open.computeIfAbsent(a.animal().id(), k -> new ArrayList<>()).add(a);
            }
        }
        Map<Long, BigDecimal> lifetime = nets(farmId, null, null);
        Map<Long, BigDecimal> last90 = nets(farmId, today.minusDays(89), today);

        List<CullRow> rows = new ArrayList<>();
        for (Cow c : cows) {
            List<String> reasons = new ArrayList<>();
            CowStatus s = status.get(c.id());
            Integer dim = c.lastCalving() == null ? null : (int) ChronoUnit.DAYS.between(c.lastCalving(), today) + 1;

            // production
            int prod;
            BigDecimal[] y = yield.get(c.id());
            if (c.category() == Category.DRY) {
                prod = 70;
                reasons.add("Dry — production not scored");
            } else if (y == null || y[1].signum() == 0) {
                prod = 60;
                reasons.add("No milk recorded in the last 90 days");
            } else {
                BigDecimal avg = y[0].divide(y[1], 1, RoundingMode.HALF_UP);
                boolean first = c.dob() != null && c.lastCalving() != null && c.dob().plusMonths(30).isAfter(c.lastCalving());
                MilkGrader.Expectation exp = MilkGrader.expect(grading, c.breed(), c.lastCalving(), today, peaks.get(c.id()), first);
                BigDecimal expected = exp != null && !exp.pastLactation() && exp.expectedLitres().signum() > 0 ? exp.expectedLitres()
                        : grading.peakFor(c.breed()).peakLitres().multiply(new BigDecimal("0.7"));
                int pct = avg.multiply(HUNDRED).divide(expected, 0, RoundingMode.HALF_UP).intValue();
                prod = clamp(pct);
                reasons.add("Yield " + avg.toPlainString() + " L/day = " + pct + " % of expected");
            }

            // reproduction
            int repro = 70;
            if (s != null) {
                switch (s.status()) {
                    case PREGNANT, DRY_PREGNANT -> {
                        repro = 100;
                        reasons.add("Pregnant" + (s.expectedDueDate() == null ? "" : " · due " + s.expectedDueDate().format(DAY)));
                    }
                    case OVERDUE -> {
                        repro = 90;
                        reasons.add("Overdue to calve");
                    }
                    case SERVED -> repro = 80;
                    case OPEN, HEIFER_NOT_SERVED -> {
                        int open90 = s.daysOpen() == null ? 0 : Math.max(0, s.daysOpen() - bs.targetDaysOpen());
                        repro = clamp(100 - open90 / 2 - s.failedServices() * 15 - s.heatsMissed() * 5);
                        if (s.daysOpen() != null) {
                            reasons.add("Open " + s.daysOpen() + " days" + (s.failedServices() > 0
                                    ? " after " + s.failedServices() + " failed service" + (s.failedServices() > 1 ? "s" : "") : ""));
                        }
                    }
                }
            }

            // health
            int n = treat.getOrDefault(c.id(), new int[] {0})[0];
            BigDecimal cost = treatCost.getOrDefault(c.id(), BigDecimal.ZERO);
            int health = 100 - n * 12 - Math.min(20, cost.divide(BigDecimal.valueOf(1000), 0, RoundingMode.DOWN).intValue() * 3);
            if (n > 0) {
                reasons.add(n + " treatment" + (n > 1 ? "s" : "") + " in 90 days");
            }
            BigDecimal sc = scc.get(c.id());
            if (sc != null && sc.compareTo(maxScc) > 0) {
                health -= 15;
                reasons.add("High SCC " + sc.stripTrailingZeros().toPlainString() + "k");
            }
            BigDecimal b = bcs.get(c.id());
            if (b != null && (b.compareTo(bcsMin) < 0 || b.compareTo(bcsMax) > 0)) {
                health -= 10;
                reasons.add("BCS " + b.stripTrailingZeros().toPlainString());
            }
            health = clamp(health);

            int score = (int) Math.round(0.45 * prod + 0.35 * repro + 0.2 * health);
            Integer age = c.dob() == null ? null : (int) ChronoUnit.YEARS.between(c.dob(), today);
            if (age != null && age >= replaceYears) {
                score -= 15;
                reasons.add("Past replacement age (" + age + " y)");
            }
            score = clamp(score);

            boolean kept = c.keepUntil() != null && !c.keepUntil().isBefore(today);
            boolean issue = open.containsKey(c.id());
            Verdict v;
            if (s != null && c.category() == Category.MILKING && (s.status() == BreedingViews.Status.PREGNANT
                    || s.status() == BreedingViews.Status.OVERDUE) && s.dryOffDate() != null && !s.dryOffDate().isAfter(today.plusDays(7))) {
                v = Verdict.DRY_OFF;
                reasons.add(0, "Dry-off due " + s.dryOffDate().format(DAY));
            } else if (score < 40 && !kept) {
                v = Verdict.CULL;
            } else if (score < 65 || issue) {
                v = Verdict.MONITOR;
                if (issue) {
                    reasons.add("Open alert: " + open.get(c.id()).get(0).title());
                }
            } else {
                v = Verdict.KEEP;
            }
            if (kept) {
                reasons.add("Kept by owner until " + c.keepUntil().format(DAY));
            }
            rows.add(new CullRow(new CullAnimal(c.id(), c.tag(), c.name(), c.breed(), age, c.lactation(), dim, c.category().name()),
                    v, score, prod, repro, health, reasons, lifetime.get(c.id()), last90.get(c.id()), c.keepUntil()));
        }
        rows.sort(Comparator.comparing((CullRow r) -> switch (r.verdict()) {
            case CULL -> 0;
            case DRY_OFF -> 1;
            case MONITOR -> 2;
            case KEEP -> 3;
        }).thenComparingInt(CullRow::score));
        VerdictCounts counts = new VerdictCounts(
                (int) rows.stream().filter(r -> r.verdict() == Verdict.KEEP).count(),
                (int) rows.stream().filter(r -> r.verdict() == Verdict.MONITOR).count(),
                (int) rows.stream().filter(r -> r.verdict() == Verdict.DRY_OFF).count(),
                (int) rows.stream().filter(r -> r.verdict() == Verdict.CULL).count());
        return new CullAdvisor(counts, rows);
    }

    @Transactional
    public CullRow keep(Long farmId, Long animalId) {
        int n = jdbc.sql("update animal set cull_keep_until = :d where id = :id and farm_id = :f")
                .param("d", DailyDates.today().plusDays(90)).param("id", animalId).param("f", farmId).update();
        if (n == 0) {
            throw ApiException.notFound("Animal not found.");
        }
        return row(farmId, animalId);
    }

    /** Marks a milking cow as dry (the advisor's "Mark as dry"). */
    @Transactional
    public CullRow dry(Long farmId, Long animalId) {
        int n = jdbc.sql("""
                update animal set category = 'DRY', updated_at = now()
                where id = :id and farm_id = :f and sex = 'FEMALE' and category = 'MILKING' and status = 'ACTIVE'""")
                .param("id", animalId).param("f", farmId).update();
        if (n == 0) {
            throw ApiException.badRequest("Only an active milking cow can be marked dry.");
        }
        return row(farmId, animalId);
    }

    private CullRow row(Long farmId, Long animalId) {
        return cullAdvisor(farmId).cows().stream().filter(r -> r.animal().id().equals(animalId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("This animal isn't in the cull advisor (adult females only)."));
    }

    private record Cow(Long id, String tag, String name, String breed, Category category, LocalDate dob, LocalDate lastCalving,
                       int lactation, LocalDate keepUntil) {
    }

    private List<Cow> cows(Long farmId) {
        return jdbc.sql("""
                select id, tag, name, breed, category, date_of_birth, last_calving_date, lactation_number, cull_keep_until
                from animal where farm_id = :f and status = 'ACTIVE' and sex = 'FEMALE' and category in ('MILKING', 'DRY')
                order by tag""")
                .param("f", farmId)
                .query((rs, i) -> new Cow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        Category.valueOf(rs.getString(5)), rs.getObject(6, LocalDate.class), rs.getObject(7, LocalDate.class),
                        rs.getInt(8), rs.getObject(9, LocalDate.class)))
                .list();
    }

    private Map<Long, BigDecimal> latest(Long farmId, String column, LocalDate since) {
        Map<Long, BigDecimal> out = new HashMap<>();
        jdbc.sql("select distinct on (animal_id) animal_id, " + column + " from production_record where farm_id = :f "
                        + "and animal_id is not null and " + column + " is not null and record_date >= :a "
                        + "order by animal_id, record_date desc, id desc")
                .param("f", farmId).param("a", since)
                .query((rs, i) -> out.put(rs.getLong(1), rs.getBigDecimal(2))).list();
        return out;
    }

    private Map<Long, BigDecimal> nets(Long farmId, LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> out = new HashMap<>();
        finance.pnl(farmId, PnlScope.ANIMAL, from, to).animals().forEach(r -> out.put(r.animal().id(), r.net()));
        return out;
    }

    // ---------- compare ----------

    @Transactional
    public Compare compare(Long farmId, Long a, Long b) {
        if (Objects.equals(a, b)) {
            throw ApiException.badRequest("Pick two different cows.");
        }
        LocalDate today = DailyDates.today();
        List<LocalDate> days = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            days.add(today.minusDays(i));
        }
        Map<Long, CowStatus> status = new HashMap<>();
        breeding.allStatuses(farmId, true).forEach(s -> status.put(s.animal().id(), s));
        Map<Long, BigDecimal> lifetime = nets(farmId, null, null);
        List<Alert> open = alerts.visibleOpen(farmId, true);
        return new Compare(card(farmId, a, days, status, lifetime, open), card(farmId, b, days, status, lifetime, open), days);
    }

    private CowCard card(Long farmId, Long id, List<LocalDate> days, Map<Long, CowStatus> status, Map<Long, BigDecimal> lifetime,
                         List<Alert> open) {
        LocalDate today = DailyDates.today();
        record A(String tag, String name, String breed, String category, LocalDate dob, LocalDate calved, int lactation, String sex) {
        }
        A a = jdbc.sql("""
                select tag, name, breed, category, date_of_birth, last_calving_date, lactation_number, sex
                from animal where id = :id and farm_id = :f""")
                .param("id", id).param("f", farmId)
                .query((rs, i) -> new A(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class), rs.getInt(7), rs.getString(8)))
                .optional().orElseThrow(() -> ApiException.notFound("Animal not found."));
        Map<LocalDate, BigDecimal> perDay = new HashMap<>();
        jdbc.sql("""
                select record_date, sum(quantity) from production_record
                where animal_id = :id and record_date between :a and :b group by record_date""")
                .param("id", id).param("a", days.get(0)).param("b", today)
                .query((rs, i) -> perDay.put(rs.getObject(1, LocalDate.class), rs.getBigDecimal(2))).list();
        List<BigDecimal> series = days.stream().map(perDay::get).toList();
        List<BigDecimal> have = series.stream().filter(Objects::nonNull).toList();
        BigDecimal avg = have.isEmpty() ? null
                : have.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(have.size()), 1, RoundingMode.HALF_UP);
        BigDecimal early = mean(series.subList(0, 4)), late = mean(series.subList(4, 7));
        BigDecimal trend = early == null || late == null || early.signum() == 0 ? null
                : late.subtract(early).multiply(HUNDRED).divide(early, 0, RoundingMode.HALF_UP);
        Integer dim = a.calved() == null || "DRY".equals(a.category()) ? null : (int) ChronoUnit.DAYS.between(a.calved(), today) + 1;
        String stage = dim == null ? null : dim <= 100 ? "Early (0–100)" : dim <= 200 ? "Mid (100–200)" : "Late (200+)";
        BigDecimal fat = jdbc.sql("""
                select round(avg(fat_pct), 2) from production_record where animal_id = :id and fat_pct is not null
                  and record_date >= :a""").param("id", id).param("a", today.minusDays(30)).query(BigDecimal.class).optional().orElse(null);
        HealthTally health = jdbc.sql("""
                select count(*), coalesce(sum(cost), 0) from farm_event where animal_id = :id and event_type = 'HEALTH'
                  and event_date >= :a""").param("id", id).param("a", today.minusDays(365))
                .query((rs, i) -> new HealthTally(rs.getInt(1), rs.getBigDecimal(2))).single();
        String issue = open.stream().filter(x -> x.animal() != null && x.animal().id().equals(id)
                        && x.ruleCode() != null && ISSUES.contains(x.ruleCode()))
                .map(Alert::title).findFirst().orElse(null);
        CowStatus s = status.get(id);
        String br = s == null ? null : switch (s.status()) {
            case PREGNANT, DRY_PREGNANT -> "Pregnant" + (s.expectedDueDate() == null ? "" : " · due " + s.expectedDueDate().format(DAY));
            case OVERDUE -> "Overdue to calve";
            case SERVED -> "Served" + (s.pdDueDate() == null ? "" : " · PD due " + s.pdDueDate().format(DAY));
            case OPEN -> "Open" + (s.servicesThisLactation() > 0 ? " · " + s.servicesThisLactation() + " service"
                    + (s.servicesThisLactation() > 1 ? "s" : "") : "");
            case HEIFER_NOT_SERVED -> "Not served";
        };
        Integer age = a.dob() == null ? null : (int) ChronoUnit.YEARS.between(a.dob(), today);
        return new CowCard(new CullAnimal(id, a.tag(), a.name(), a.breed(), age, a.lactation(), dim, a.category()), a.breed(),
                cap(a.category()), dim, stage, series, avg, trend, fat, health, issue, br, lifetime.get(id));
    }

    // ---------- helpers ----------

    private static BigDecimal mean(List<BigDecimal> xs) {
        List<BigDecimal> have = xs.stream().filter(Objects::nonNull).toList();
        return have.isEmpty() ? null
                : have.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(have.size()), 2, RoundingMode.HALF_UP);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static BigDecimal z(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal sum(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(r -> r.get(key)).filter(v -> v instanceof BigDecimal).map(v -> (BigDecimal) v)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Column col(String key, String label) {
        return new Column(key, label);
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String l = s.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }
}
