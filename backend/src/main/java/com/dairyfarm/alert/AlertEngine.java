package com.dairyfarm.alert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.alert.AlertRepository.Details;
import com.dairyfarm.alert.AlertRepository.Hit;
import com.dairyfarm.alert.AlertRuleBook.Resolved;
import com.dairyfarm.alert.AlertViews.Action;
import com.dairyfarm.alert.AlertViews.ActionKind;
import com.dairyfarm.alert.AlertViews.AlertCategory;
import com.dairyfarm.alert.AlertViews.Fact;
import com.dairyfarm.alert.AlertViews.RefreshResult;
import com.dairyfarm.alert.AlertViews.Severity;
import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.breeding.BreedingQueryService;
import com.dairyfarm.breeding.BreedingViews;
import com.dairyfarm.breeding.BreedingViews.CalfItem;
import com.dairyfarm.breeding.BreedingViews.CowStatus;
import com.dairyfarm.breeding.BreedingViews.SemenBatch;
import com.dairyfarm.breeding.CalfService;
import com.dairyfarm.breeding.SemenService;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.equipment.EquipmentService;
import com.dairyfarm.equipment.EquipmentViews.Equipment;
import com.dairyfarm.equipment.EquipmentViews.ServiceStatus;
import com.dairyfarm.farm.Farm;
import com.dairyfarm.farm.FarmRepository;
import com.dairyfarm.farm.FarmService;
import com.dairyfarm.feed.FeedService;
import com.dairyfarm.feed.FeedViews.StockItem;
import com.dairyfarm.feed.FeedViews.StockStatus;
import com.dairyfarm.finance.FinanceService;
import com.dairyfarm.finance.FinanceViews.Summary;
import com.dairyfarm.record.DailyDates;

/** Works out which alert rules hit today and keeps the alert table in step with them. */
@Service
public class AlertEngine {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_Y = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final FarmRepository farms;
    private final AlertRuleBook book;
    private final AlertRepository alerts;
    private final BreedingQueryService breeding;
    private final CalfService calves;
    private final FeedService feed;
    private final SemenService semen;
    private final EquipmentService equipment;
    private final FinanceService finance;
    private final HealthScheduleService schedules;
    private final JdbcClient jdbc;

    public AlertEngine(FarmRepository farms, AlertRuleBook book, AlertRepository alerts, BreedingQueryService breeding,
                       CalfService calves, FeedService feed, SemenService semen, EquipmentService equipment,
                       FinanceService finance, HealthScheduleService schedules, JdbcClient jdbc) {
        this.farms = farms;
        this.book = book;
        this.alerts = alerts;
        this.breeding = breeding;
        this.calves = calves;
        this.feed = feed;
        this.semen = semen;
        this.equipment = equipment;
        this.finance = finance;
        this.schedules = schedules;
        this.jdbc = jdbc;
    }

    @Transactional
    public RefreshResult refresh(Long farmId) {
        Farm farm = farms.findById(farmId).orElseThrow(() -> ApiException.notFound("Farm not found."));
        int[] r = alerts.apply(farmId, hits(farm));
        alerts.markRefreshed(farmId);
        return new RefreshResult(r[0], r[1]);
    }

    /** Refreshes when the last run is more than an hour old. */
    @Transactional
    public void refreshIfStale(Long farmId) {
        Instant last = alerts.lastRefresh(farmId);
        if (last == null || last.isBefore(Instant.now().minus(1, ChronoUnit.HOURS))) {
            refresh(farmId);
        }
    }

    List<Hit> hits(Farm farm) {
        Long farmId = farm.getId();
        LocalDate today = DailyDates.today();
        Map<String, Resolved> rules = book.resolve(farm);
        List<Hit> out = new ArrayList<>();
        H h = new H(rules, out);
        List<CowStatus> cows = breeding.allStatuses(farmId, true);
        reproduction(h, cows, today);
        if (h.on("COLOSTRUM_DUE") || h.on("WEANING")) {
            calf(h, calves.list(farmId, null, null, null).calves(), today);
        }
        if (h.on("HEIFER_FIRST_AI")) {
            for (CowStatus c : cows) {
                if (c.suggestion().action() == BreedingViews.Action.FIRST_SERVICE_DUE) {
                    h.add("HEIFER_FIRST_AI", "HEIFER_FIRST_AI:" + c.animal().id(), null, "Heifer first AI due",
                            label(c.animal()) + " · heifer of service age, not served yet", c.animal().id(), "ANIMAL",
                            c.animal().id(), today, null, details("She has reached the first-service age or weight set in the rules.",
                                    List.of(fact("Rule", rules.get("HEIFER_FIRST_AI").i("heiferFirstServiceKg") + " kg or "
                                            + rules.get("HEIFER_FIRST_AI").i("heiferFirstServiceMonths") + " months")),
                                    act(ActionKind.LOG_SERVICE, "Record service", "animalId", c.animal().id())));
                }
            }
        }
        if (h.on("WEIGHT_OVERDUE")) {
            weightOverdue(h, farmId, today);
        }
        if (h.on("BCS_RANGE")) {
            bcs(h, farmId, today);
        }
        withdrawals(h, farmId);
        if (h.on("HEALTH_FOLLOW_UP")) {
            followUps(h, farmId, today);
        }
        if (h.on("SCHEDULE_DUE")) {
            for (HealthScheduleService.Due d : schedules.due(farmId, today)) {
                String kind = d.schedule().type() == AlertViews.ScheduleType.DEWORMING ? "Deworming" : "Vaccination";
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("animalId", d.animalId());
                p.put("type", d.schedule().type().name());
                p.put("scheduleId", d.schedule().id());
                p.put("step", d.stepKey());
                if (d.schedule().medicine() != null) {
                    p.put("medicine", d.schedule().medicine());
                }
                h.add("SCHEDULE_DUE", "SCHEDULE:" + d.schedule().id() + ":" + d.animalId() + ":" + d.stepKey(),
                        d.date().isBefore(today) ? Severity.HIGH : null, kind + " due — " + d.stepLabel(),
                        tagName(d.tag(), d.name()) + " · " + d.schedule().name(), d.animalId(), "SCHEDULE", d.schedule().id(),
                        d.date(), null, d.schedule().workerVisible(),
                        details("From the schedule \"" + d.schedule().name() + "\".", List.of(fact("Dose", d.stepLabel()),
                                fact("Due on", d.date().format(DAY_Y))), new Action(ActionKind.LOG_HEALTH, "Record " + kind.toLowerCase(), p)));
            }
        }
        milk(h, farmId, today);
        if (h.on("FEED_REORDER")) {
            for (StockItem s : feed.overview(farmId, today, true).items()) {
                if (s.status() == StockStatus.REORDER || s.status() == StockStatus.PREPARE) {
                    boolean mix = s.status() == StockStatus.PREPARE;
                    h.add("FEED_REORDER", "FEED_REORDER:" + s.item().id(), null,
                            (mix ? "Prepare " : "Reorder ") + s.item().name(),
                            num(s.stock()) + " " + s.item().unit().toLowerCase() + " left"
                                    + (s.daysLeft() == null ? "" : " · " + num(s.daysLeft()) + " days"),
                            null, "ITEM", s.item().id(), today, null,
                            details("Stock is below the reorder level or will run out within the lead time.", List.of(),
                                    act(ActionKind.OPEN_FEED, mix ? "Prepare batch" : "Buy", "itemId", s.item().id())));
                }
            }
        }
        if (h.on("SEMEN_LOW")) {
            int min = rules.get("SEMEN_LOW").i("minStraws");
            for (SemenBatch b : semen.list(farmId, true).batches()) {
                boolean expired = b.expiryDate() != null && b.expiryDate().isBefore(today);
                boolean recent = b.straws() > 0 || b.usedUpOn() == null || !b.usedUpOn().isBefore(today.minusDays(30));
                if (b.straws() < min && !expired && recent) {
                    h.add("SEMEN_LOW", "SEMEN_LOW:" + b.id(), null, "Semen stock low",
                            b.straws() + " straws " + (b.breed() == null ? "" : b.breed() + " ") + b.bullName(), null, "ITEM",
                            b.id(), today, null, details("Fewer than " + min + " straws left.", List.of(),
                                    act(ActionKind.OPEN_PROCUREMENT, "Plan purchase", "itemId", b.id())));
                }
            }
        }
        if (h.on("REPLACEMENT_AGE")) {
            int years = rules.get("REPLACEMENT_AGE").i("ageYears");
            jdbc.sql("""
                    select id, tag, name, date_of_birth from animal where farm_id = :f and status = 'ACTIVE' and sex = 'FEMALE'
                      and category in ('MILKING', 'DRY') and date_of_birth <= :d order by tag""")
                    .param("f", farmId).param("d", today.minusYears(years))
                    .query((rs, i) -> {
                        long id = rs.getLong(1);
                        LocalDate dob = rs.getObject(4, LocalDate.class);
                        h.add("REPLACEMENT_AGE", "REPLACEMENT:" + id, null, "Cow replacement due",
                                tagName(rs.getString(2), rs.getString(3)) + " · " + ChronoUnit.YEARS.between(dob, today) + " years old",
                                id, "ANIMAL", id, today, null, details("She is past the replacement age of " + years
                                        + " years. Check her in the cull advisor.", List.of(), Action.NONE));
                        return 0;
                    }).list();
        }
        if (h.on("LOW_MARGIN")) {
            Summary s = finance.summary(farmId, today.minusDays(6), today);
            int pct = rules.get("LOW_MARGIN").i("marginAlertPct");
            if (s.income().signum() > 0 && s.marginPct() != null && s.marginPct().compareTo(BigDecimal.valueOf(pct)) < 0) {
                LocalDate week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                h.add("LOW_MARGIN", "LOW_MARGIN:" + week, null, "Low margin this week",
                        "Margin " + num(s.marginPct()) + " % vs " + pct + " % target", null, null, null, today, null,
                        details("Last 7 days: income ₹" + num(s.income()) + ", expense ₹" + num(s.expense()) + ".",
                                List.of(fact("Margin", num(s.marginPct()) + " %"), fact("Target", pct + " %")), Action.NONE));
            }
        }
        if (h.on("EQUIPMENT_SERVICE")) {
            for (Equipment e : equipment.list(farmId).items()) {
                if (e.status() == ServiceStatus.OVERDUE || e.status() == ServiceStatus.DUE_SOON) {
                    boolean over = e.status() == ServiceStatus.OVERDUE;
                    h.add("EQUIPMENT_SERVICE", "EQUIPMENT:" + e.id() + ":" + e.nextService(), over ? Severity.HIGH : null,
                            "Service due — " + e.name(), (e.serviceTask() == null ? "Service" : e.serviceTask())
                                    + " · " + (over ? "was due " : "due ") + e.nextService().format(DAY),
                            null, "EQUIPMENT", e.id(), e.nextService(), null,
                            details("Last service " + (e.lastService() == null ? "not recorded" : e.lastService().format(DAY_Y)) + ".",
                                    List.of(), act(ActionKind.OPEN_EQUIPMENT, "Log service", "equipmentId", e.id())));
                }
            }
        }
        if (h.on("PROCUREMENT_DUE")) {
            jdbc.sql("""
                    select id, title, needed_by, status from procurement_note where farm_id = :f and status in ('PLANNED', 'ORDERED')
                      and needed_by is not null and needed_by <= :d""")
                    .param("f", farmId).param("d", today.plusDays(2))
                    .query((rs, i) -> {
                        long id = rs.getLong(1);
                        LocalDate by = rs.getObject(3, LocalDate.class);
                        h.add("PROCUREMENT_DUE", "PROCUREMENT:" + id, null, "Needed by " + by.format(DAY) + " — " + rs.getString(2),
                                rs.getString(4).equals("ORDERED") ? "Ordered, not received yet" : "Planned, not ordered yet",
                                null, "PROCUREMENT", id, by, null,
                                details(null, List.of(), act(ActionKind.OPEN_PROCUREMENT, "Open note", "noteId", id)));
                        return 0;
                    }).list();
        }
        return out;
    }

    // ---------- reproduction ----------

    private void reproduction(H h, List<CowStatus> cows, LocalDate today) {
        Map<String, Resolved> r = h.rules;
        for (CowStatus c : cows) {
            Long id = c.animal().id();
            String who = label(c.animal());
            BreedingViews.Status st = c.status();
            boolean open = st == BreedingViews.Status.OPEN || st == BreedingViews.Status.HEIFER_NOT_SERVED;
            boolean pregnant = st == BreedingViews.Status.PREGNANT || st == BreedingViews.Status.DRY_PREGNANT
                    || st == BreedingViews.Status.OVERDUE;
            if (h.on("HEAT_EXPECTED") && open && c.nextHeatExpected() != null
                    && !c.nextHeatExpected().isBefore(today.minusDays(2)) && !c.nextHeatExpected().isAfter(today.plusDays(7))) {
                String last = c.lastHeatDate() == null ? "no heat recorded"
                        : "last heat " + c.lastHeatDate().format(DAY) + " (" + ChronoUnit.DAYS.between(c.lastHeatDate(), today) + " d)";
                h.add("HEAT_EXPECTED", "HEAT_EXPECTED:" + id + ":" + c.nextHeatExpected(), null, "Heat expected", who + " · " + last,
                        id, "ANIMAL", id, c.nextHeatExpected().isBefore(today) ? today : c.nextHeatExpected(), null,
                        details("Her next cycle is expected around " + c.nextHeatExpected().format(DAY) + " (± 2 days). Watch her morning and evening.",
                                List.of(fact("Rule", r.get("HEAT_EXPECTED").i("heatCycleDays") + " days after last heat"),
                                        fact("Expected", c.nextHeatExpected().format(DAY_Y))),
                                act(ActionKind.LOG_HEAT, "Log heat", "animalId", id)));
            }
            if (h.on("PD_DUE") && st == BreedingViews.Status.SERVED && c.pdDueDate() != null
                    && !c.pdDueDate().isAfter(today.plusDays(7))) {
                long ago = c.lastServiceDate() == null ? 0 : ChronoUnit.DAYS.between(c.lastServiceDate(), today);
                boolean over = c.pdDueDate().isBefore(today);
                h.add("PD_DUE", "PD_DUE:" + c.currentServiceId(), over ? Severity.HIGH : null, "Pregnancy check due",
                        who + " · served " + (c.lastServiceDate() == null ? "" : c.lastServiceDate().format(DAY)) + " · " + ago + " days ago",
                        id, "SERVICE", c.currentServiceId(), c.pdDueDate(), null,
                        details(name(c.animal()) + " was served " + ago + " days ago. The rule asks for a pregnancy check "
                                        + r.get("PD_DUE").i("pdAfterDays") + " days after service, and no check has been recorded yet.",
                                List.of(fact("Rule", "Pregnancy check · " + r.get("PD_DUE").i("pdAfterDays") + " d after service"),
                                        fact("Service date", c.lastServiceDate() == null ? "—" : c.lastServiceDate().format(DAY_Y)),
                                        fact("Due on", c.pdDueDate().format(DAY_Y)),
                                        fact("Last heat", c.lastHeatDate() == null ? "—" : c.lastHeatDate().format(DAY_Y))),
                                act(ActionKind.LOG_PD, "Log pregnancy check", "animalId", id, "serviceId", c.currentServiceId())));
            }
            if (h.on("CALVING_SOON") && pregnant && c.expectedDueDate() != null
                    && !c.expectedDueDate().isAfter(today.plusDays(r.get("CALVING_SOON").i("daysBefore")))) {
                boolean over = c.expectedDueDate().isBefore(today);
                h.add("CALVING_SOON", "CALVING:" + c.currentServiceId() + ":" + c.expectedDueDate(), over ? Severity.HIGH : null,
                        over ? "Calving overdue" : "Calving due soon", who + " · expected " + c.expectedDueDate().format(DAY),
                        id, "SERVICE", c.currentServiceId(), c.expectedDueDate(), null,
                        details("Prepare a clean calving pen and colostrum.", List.of(fact("Expected", c.expectedDueDate().format(DAY_Y))),
                                act(ActionKind.LOG_CALVING, "Record calving", "animalId", id)));
            }
            if (h.on("NO_HEAT_AFTER_CALVING") && st == BreedingViews.Status.OPEN && c.lastCalvingDate() != null
                    && c.daysOpen() != null && c.daysOpen() >= r.get("NO_HEAT_AFTER_CALVING").i("days")
                    && (c.lastHeatDate() == null || c.lastHeatDate().isBefore(c.lastCalvingDate()))
                    && c.lastServiceDate() == null) {
                LocalDate due = c.lastCalvingDate().plusDays(r.get("NO_HEAT_AFTER_CALVING").i("days"));
                h.add("NO_HEAT_AFTER_CALVING", "NO_HEAT:" + id + ":" + c.lastCalvingDate(), null, "No heat since calving",
                        who + " · calved " + c.lastCalvingDate().format(DAY) + " (" + c.daysOpen() + " d)", id, "ANIMAL", id, due, null,
                        details("No heat has been recorded since she calved. It may be a silent heat — consider a vet check.",
                                List.of(fact("Calved", c.lastCalvingDate().format(DAY_Y)), fact("Days open", String.valueOf(c.daysOpen()))),
                                act(ActionKind.LOG_HEAT, "Log heat", "animalId", id)));
            }
            if (h.on("LONG_CALVING_INTERVAL") && st == BreedingViews.Status.OPEN && c.lastCalvingDate() != null && c.daysOpen() != null) {
                int limit = r.get("LONG_CALVING_INTERVAL").i("days");
                int gestation = 283;
                if (c.daysOpen() + gestation > limit) {
                    h.add("LONG_CALVING_INTERVAL", "LONG_INTERVAL:" + id + ":" + c.lastCalvingDate(), null, "Calving interval getting long",
                            who + " · " + c.daysOpen() + " days open → interval over " + limit + " d", id, "ANIMAL", id,
                            c.lastCalvingDate().plusDays(Math.max(0, limit - gestation)), null,
                            details("Even if she conceives now, her calving interval will be over " + limit + " days.", List.of(),
                                    act(ActionKind.LOG_SERVICE, "Record service", "animalId", id)));
                }
            }
            if (h.on("DRY_OFF_DUE") && c.dryOffDate() != null && c.category() == Category.MILKING
                    && (st == BreedingViews.Status.PREGNANT || st == BreedingViews.Status.OVERDUE)
                    && !c.dryOffDate().isAfter(today.plusDays(7))) {
                h.add("DRY_OFF_DUE", "DRY_OFF:" + c.currentServiceId() + ":" + c.dryOffDate(), null, "Dry-off due",
                        who + " · dry off by " + c.dryOffDate().format(DAY) + " · due " + (c.expectedDueDate() == null ? "—"
                                : c.expectedDueDate().format(DAY)), id, "ANIMAL", id, c.dryOffDate(), null,
                        details("Dry her off " + r.get("DRY_OFF_DUE").i("dryOffDaysBeforeDue") + " days before calving.",
                                List.of(fact("Expected calving", c.expectedDueDate() == null ? "—" : c.expectedDueDate().format(DAY_Y))),
                                Action.NONE));
            }
        }
    }

    // ---------- calves ----------

    private void calf(H h, List<CalfItem> list, LocalDate today) {
        for (CalfItem c : list) {
            Long id = c.animal().id();
            if (h.on("COLOSTRUM_DUE") && c.colostrum() != null && !c.colostrum().given() && c.colostrum().dueBy() != null
                    && c.ageDays() != null && c.ageDays() <= 2) {
                LocalDateTime by = LocalDateTime.ofInstant(c.colostrum().dueBy(), FarmService.FARM_ZONE);
                h.add("COLOSTRUM_DUE", "COLOSTRUM:" + id, null, "Colostrum due", label(c.animal()) + " · give by "
                        + by.format(TIME), id, "ANIMAL", id, by.toLocalDate(), by.toLocalTime().withSecond(0).withNano(0),
                        details("A newborn calf needs colostrum within " + h.rules.get("COLOSTRUM_DUE").i("colostrumHours")
                                + " hours of birth.", List.of(), act(ActionKind.NONE, null)));
            }
            if (h.on("WEANING") && c.weaning() != null && c.weaning().ready()) {
                h.add("WEANING", "WEANING:" + id, null, "Ready to wean", label(c.animal()) + " · " + c.ageDays() + " days",
                        id, "ANIMAL", id, today, null, details("Age and starter intake meet the weaning rule.", List.of(), Action.NONE));
            }
        }
    }

    private void weightOverdue(H h, Long farmId, LocalDate today) {
        int every = h.rules.get("WEIGHT_OVERDUE").i("everyDays");
        jdbc.sql("""
                select a.id, a.tag, a.name, a.date_of_birth,
                       (select max(e.event_date) from farm_event e where e.animal_id = a.id and e.event_type = 'MEASUREMENT'
                          and e.weight_kg is not null) as last
                from animal a where a.farm_id = :f and a.status = 'ACTIVE' and a.category in ('CALF', 'HEIFER')""")
                .param("f", farmId)
                .query((rs, i) -> {
                    long id = rs.getLong(1);
                    LocalDate dob = rs.getObject(4, LocalDate.class);
                    LocalDate last = rs.getObject(5, LocalDate.class);
                    LocalDate base = last != null ? last : dob;
                    if (base != null) {
                        LocalDate due = base.plusDays(every);
                        if (!due.isAfter(today)) {
                            h.add("WEIGHT_OVERDUE", "WEIGHT:" + id + ":" + due, null, "Weight entry overdue",
                                    tagName(rs.getString(2), rs.getString(3)) + " · "
                                            + (last == null ? "never weighed" : "last weighed " + last.format(DAY)),
                                    id, "ANIMAL", id, due, null, details("Calves and heifers need a weight entry every " + every + " days.",
                                            List.of(), act(ActionKind.LOG_WEIGHT, "Log weight", "animalId", id)));
                        }
                    }
                    return 0;
                }).list();
    }

    // ---------- health ----------

    private void bcs(H h, Long farmId, LocalDate today) {
        BigDecimal min = h.rules.get("BCS_RANGE").p("min"), max = h.rules.get("BCS_RANGE").p("max");
        jdbc.sql("""
                select distinct on (e.animal_id) e.id, e.animal_id, e.bcs, e.event_date, a.tag, a.name
                from farm_event e join animal a on a.id = e.animal_id
                where e.farm_id = :f and e.event_type = 'MEASUREMENT' and e.bcs is not null and a.status = 'ACTIVE'
                order by e.animal_id, e.event_date desc, e.id desc""")
                .param("f", farmId)
                .query((rs, i) -> {
                    BigDecimal b = rs.getBigDecimal(3);
                    LocalDate d = rs.getObject(4, LocalDate.class);
                    long animal = rs.getLong(2);
                    if (!d.isBefore(today.minusDays(60)) && (b.compareTo(min) < 0 || b.compareTo(max) > 0)) {
                        h.add("BCS_RANGE", "BCS:" + rs.getLong(1), null, "BCS " + (b.compareTo(min) < 0 ? "too low" : "too high")
                                + " — " + num(b), tagName(rs.getString(5), rs.getString(6)) + " · scored " + d.format(DAY),
                                animal, "MEASUREMENT", rs.getLong(1), today, null,
                                details("Body condition should stay between " + num(min) + " and " + num(max) + ".", List.of(),
                                        act(ActionKind.LOG_WEIGHT, "Log BCS", "animalId", animal)));
                    }
                    return 0;
                }).list();
    }

    private void withdrawals(H h, Long farmId) {
        jdbc.sql("""
                select e.id, e.animal_id, e.event_date, e.milk_withdrawal_until, e.details->>'medicine', a.tag, a.name
                from farm_event e join animal a on a.id = e.animal_id
                where e.farm_id = :f and e.event_type = 'HEALTH' and e.milk_withdrawal_until > now() and a.status = 'ACTIVE'""")
                .param("f", farmId)
                .query((rs, i) -> {
                    long id = rs.getLong(1);
                    long animal = rs.getLong(2);
                    LocalDateTime ends = LocalDateTime.ofInstant(rs.getTimestamp(4).toInstant(), FarmService.FARM_ZONE);
                    long left = ChronoUnit.DAYS.between(DailyDates.today(), ends.toLocalDate());
                    String med = rs.getString(5);
                    h.add("WITHDRAWAL", "WITHDRAWAL:" + id, null, "Withdrawal active — do NOT sell milk",
                            tagName(rs.getString(6), rs.getString(7)) + (med == null ? "" : " · " + med) + " · "
                                    + (left <= 0 ? "ends today" : left + " d left"),
                            animal, "HEALTH", id, DailyDates.today(), null,
                            details("Milk her separately and discard it until " + ends.format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a",
                                    Locale.ENGLISH)) + ".", List.of(fact("Ends", ends.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a",
                                    Locale.ENGLISH)))), act(ActionKind.LOG_HEALTH, "Open record", "healthRecordId", id, "animalId", animal)));
                    return 0;
                }).list();
    }

    private void followUps(H h, Long farmId, LocalDate today) {
        jdbc.sql("""
                select e.id, e.animal_id, e.next_action_date, e.next_action, e.sub_type, a.tag, a.name
                from farm_event e join animal a on a.id = e.animal_id
                where e.farm_id = :f and e.event_type = 'HEALTH' and e.next_action_date is not null and not e.next_action_done
                  and e.next_action_date <= :until and a.status = 'ACTIVE'""")
                .param("f", farmId).param("until", today.plusDays(7))
                .query((rs, i) -> {
                    long id = rs.getLong(1);
                    LocalDate due = rs.getObject(3, LocalDate.class);
                    String what = rs.getString(4);
                    h.add("HEALTH_FOLLOW_UP", "FOLLOW_UP:" + id + ":" + due, due.isBefore(today) ? Severity.HIGH : null,
                            what == null ? "Health follow-up" : what, tagName(rs.getString(6), rs.getString(7)) + " · "
                                    + rs.getString(5).toLowerCase(Locale.ROOT) + " follow-up",
                            rs.getLong(2), "HEALTH", id, due, null,
                            details("Next action set on a health record.", List.of(fact("Due on", due.format(DAY_Y))),
                                    act(ActionKind.LOG_HEALTH, "Open record", "healthRecordId", id, "animalId", rs.getLong(2))));
                    return 0;
                }).list();
    }

    // ---------- milk ----------

    private void milk(H h, Long farmId, LocalDate today) {
        if (h.on("YIELD_DROP")) {
            int pct = h.rules.get("YIELD_DROP").i("dropAlertPct");
            jdbc.sql("""
                    with latest as (
                      select distinct on (p.animal_id) p.animal_id, p.record_date, p.session, p.quantity
                      from production_record p join animal a on a.id = p.animal_id
                      where p.farm_id = :f and p.animal_id is not null and p.record_date >= :from and a.status = 'ACTIVE'
                        and p.quantity is not null
                      order by p.animal_id, p.record_date desc, p.session desc)
                    select l.animal_id, l.record_date, l.session, l.quantity, a.tag, a.name,
                           (select avg(x.quantity) from (select q.quantity from production_record q
                              where q.animal_id = l.animal_id and q.session = l.session and q.record_date < l.record_date
                                and q.quantity is not null order by q.record_date desc limit 7) x) as avg7
                    from latest l join animal a on a.id = l.animal_id""")
                    .param("f", farmId).param("from", today.minusDays(1))
                    .query((rs, i) -> {
                        BigDecimal q = rs.getBigDecimal(4), avg = rs.getBigDecimal(7);
                        if (avg != null && avg.signum() > 0) {
                            BigDecimal drop = avg.subtract(q).multiply(BigDecimal.valueOf(100)).divide(avg, 0, RoundingMode.HALF_UP);
                            if (drop.intValue() >= pct) {
                                long animal = rs.getLong(1);
                                LocalDate d = rs.getObject(2, LocalDate.class);
                                String session = rs.getString(3);
                                h.add("YIELD_DROP", "YIELD_DROP:" + animal + ":" + d + ":" + session, null,
                                        "Sudden yield drop −" + drop + " %", tagName(rs.getString(5), rs.getString(6)) + " · "
                                                + num(q) + " L " + session + " vs " + num(avg.setScale(1, RoundingMode.HALF_UP)) + " L avg",
                                        animal, "ANIMAL", animal, d, null,
                                        details("A sudden drop can be the first sign of mastitis, fever or heat stress. Check her.",
                                                List.of(fact("Rule", "more than " + pct + " % below her last 7 " + session + " records")),
                                                act(ActionKind.LOG_HEALTH, "Record health check", "animalId", animal)));
                            }
                        }
                        return 0;
                    }).list();
        }
        if (h.on("LOW_FAT") || h.on("HIGH_SCC")) {
            BigDecimal minFat = h.rules.get("LOW_FAT").p("minFatPct");
            BigDecimal maxScc = h.rules.get("HIGH_SCC").p("maxSccK");
            jdbc.sql("""
                    select distinct on (p.animal_id, kind) p.animal_id, p.record_date, kind, val, a.tag, a.name
                    from (select animal_id, record_date, 'FAT' as kind, fat_pct as val, id from production_record
                            where farm_id = :f and animal_id is not null and fat_pct is not null and record_date >= :from30
                          union all
                          select animal_id, record_date, 'SCC', scc, id from production_record
                            where farm_id = :f and animal_id is not null and scc is not null and record_date >= :from30) p
                    join animal a on a.id = p.animal_id where a.status = 'ACTIVE'
                    order by p.animal_id, kind, p.record_date desc, p.id desc""")
                    .param("f", farmId).param("from30", today.minusDays(30))
                    .query((rs, i) -> {
                        long animal = rs.getLong(1);
                        LocalDate d = rs.getObject(2, LocalDate.class);
                        BigDecimal v = rs.getBigDecimal(4);
                        String who = tagName(rs.getString(5), rs.getString(6));
                        if ("FAT".equals(rs.getString(3))) {
                            if (h.on("LOW_FAT") && !d.isBefore(today.minusDays(7)) && v.compareTo(minFat) < 0) {
                                h.add("LOW_FAT", "LOW_FAT:" + animal + ":" + d, null, "Low fat " + num(v) + " %",
                                        who + " · tested " + d.format(DAY), animal, "ANIMAL", animal, today, null,
                                        details("Below " + num(minFat) + " % — check her ration (fibre) and the buyer's price slab.",
                                                List.of(), Action.NONE));
                            }
                        } else if (h.on("HIGH_SCC") && v.compareTo(maxScc) > 0) {
                            h.add("HIGH_SCC", "HIGH_SCC:" + animal + ":" + d, null, "High SCC — check udder",
                                    who + " · " + num(v) + "k cells/mL on " + d.format(DAY), animal, "ANIMAL", animal, today, null,
                                    details("Above " + num(maxScc) + "k suggests sub-clinical mastitis. Do a CMT / strip test.",
                                            List.of(), act(ActionKind.LOG_HEALTH, "Record health check", "animalId", animal)));
                        }
                        return 0;
                    }).list();
        }
    }

    // ---------- helpers ----------

    /** Collects hits, applying each rule's on/off, worker visibility and default severity. */
    private static final class H {
        final Map<String, Resolved> rules;
        final List<Hit> out;

        H(Map<String, Resolved> rules, List<Hit> out) {
            this.rules = rules;
            this.out = out;
        }

        boolean on(String code) {
            return rules.get(code).enabled();
        }

        void add(String code, String key, Severity severity, String title, String detail, Long animalId, String relatedType,
                 Long relatedId, LocalDate due, LocalTime time, Details d) {
            add(code, key, severity, title, detail, animalId, relatedType, relatedId, due, time, true, d);
        }

        void add(String code, String key, Severity severity, String title, String detail, Long animalId, String relatedType,
                 Long relatedId, LocalDate due, LocalTime time, boolean visible, Details d) {
            Resolved r = rules.get(code);
            if (!r.enabled()) {
                return;
            }
            AlertCategory cat = r.def().category();
            out.add(new Hit(code, key.length() > 160 ? key.substring(0, 160) : key, cat,
                    severity != null ? severity : r.def().severity(), title, detail, animalId, relatedType, relatedId, due, time,
                    r.workerVisible() && visible, d));
        }
    }

    private static Details details(String why, List<Fact> facts, Action action) {
        return new Details(why, facts, action, null);
    }

    private static Fact fact(String label, String value) {
        return new Fact(label, value);
    }

    private static Action act(ActionKind kind, String label, Object... kv) {
        Map<String, Object> p = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                p.put((String) kv[i], kv[i + 1]);
            }
        }
        return new Action(kind, label, p);
    }

    private static String label(BreedingViews.AnimalRef a) {
        return tagName(a.tag(), a.name());
    }

    private static String name(BreedingViews.AnimalRef a) {
        return a.name() != null ? a.name() : a.tag();
    }

    static String tagName(String tag, String name) {
        return name == null || name.isBlank() ? tag : tag + " " + name;
    }

    static String num(BigDecimal v) {
        if (v == null) {
            return "—";
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0).toPlainString() : s.toPlainString();
    }
}
