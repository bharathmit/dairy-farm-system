package com.dairyfarm.breeding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.animal.Animal;
import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.animal.AnimalEnums.Status;
import com.dairyfarm.animal.AnimalRepository;
import com.dairyfarm.breeding.BreedingRecord.Method;
import com.dairyfarm.breeding.BreedingRecord.Outcome;
import com.dairyfarm.breeding.BreedingRecord.Pregnancy;
import com.dairyfarm.breeding.BreedingStatusCalc.Cow;
import com.dairyfarm.breeding.BreedingStatusCalc.Result;
import com.dairyfarm.breeding.BreedingStatusCalc.Svc;
import com.dairyfarm.breeding.BreedingViews.CalendarItem;
import com.dairyfarm.breeding.BreedingViews.CowStatus;
import com.dairyfarm.breeding.BreedingViews.Current;
import com.dairyfarm.breeding.BreedingViews.Groups;
import com.dairyfarm.breeding.BreedingViews.HeatWatchItem;
import com.dairyfarm.breeding.BreedingViews.History;
import com.dairyfarm.breeding.BreedingViews.HistoryEvent;
import com.dairyfarm.breeding.BreedingViews.Lactation;
import com.dairyfarm.breeding.BreedingViews.Overview;
import com.dairyfarm.breeding.BreedingViews.Totals;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.common.Sex;
import com.dairyfarm.contact.Contact;
import com.dairyfarm.contact.ContactRepository;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.record.FarmEvent;
import com.dairyfarm.record.FarmEvent.EventType;
import com.dairyfarm.record.FarmEventRepository;
import com.dairyfarm.stock.Item;
import com.dairyfarm.stock.ItemRepository;

/** Breeding status of every breedable female: overview, heat watch and one cow's history. */
@Service
public class BreedingQueryService {

    private final AnimalRepository animals;
    private final BreedingRecordRepository records;
    private final FarmEventRepository events;
    private final BreedingSql sql;
    private final BreedingService breeding;
    private final ContactRepository contacts;
    private final ItemRepository items;

    public BreedingQueryService(AnimalRepository animals, BreedingRecordRepository records, FarmEventRepository events,
                                BreedingSql sql, BreedingService breeding, ContactRepository contacts, ItemRepository items) {
        this.animals = animals;
        this.records = records;
        this.events = events;
        this.sql = sql;
        this.breeding = breeding;
        this.contacts = contacts;
        this.items = items;
    }

    @Transactional(readOnly = true)
    public Overview overview(Long farmId, boolean showMoney) {
        LocalDate today = DailyDates.today();
        List<Result> all = statuses(farmId, breedable(farmId, today), today, showMoney);
        List<CowStatus> overdue = new ArrayList<>(), dueIn7 = new ArrayList<>(), open = new ArrayList<>(),
                served = new ArrayList<>(), pregnant = new ArrayList<>(), dry = new ArrayList<>();
        List<CalendarItem> calendar = new ArrayList<>();
        LocalDate last = today.plusDays(13);
        for (Result r : all) {
            CowStatus s = r.status();
            switch (s.status()) {
                case OVERDUE -> overdue.add(s);
                case OPEN, HEIFER_NOT_SERVED -> open.add(s);
                case SERVED -> served.add(s);
                case PREGNANT, DRY_PREGNANT -> {
                    if (s.daysToDue() != null && s.daysToDue() <= 7) {
                        dueIn7.add(s);
                    } else if (s.status() == BreedingViews.Status.PREGNANT) {
                        pregnant.add(s);
                    }
                    if (s.status() == BreedingViews.Status.DRY_PREGNANT) {
                        dry.add(s);
                    }
                }
            }
            if (s.status() == BreedingViews.Status.OVERDUE && s.category() == Category.DRY) {
                dry.add(s);
            }
            addDay(calendar, today, last, s.lastHeatDate(), "HEAT", s);
            if (s.status() == BreedingViews.Status.OPEN || s.status() == BreedingViews.Status.HEIFER_NOT_SERVED) {
                addDay(calendar, today, last, s.nextHeatExpected(), "HEAT_EXPECTED", s);
            }
            addDay(calendar, today, last, s.pdDueDate(), "PD", s);
            if (s.category() != Category.DRY) {
                addDay(calendar, today, last, s.dryOffDate(), "DRY_OFF", s);
            }
            addDay(calendar, today, last, s.status() == BreedingViews.Status.SERVED ? null : s.expectedDueDate(), "CALVING", s);
        }
        calendar.sort(Comparator.comparing(CalendarItem::date).thenComparing(CalendarItem::kind));
        overdue.sort(Comparator.comparing(CowStatus::daysToDue));
        dueIn7.sort(Comparator.comparing(CowStatus::daysToDue));
        pregnant.sort(Comparator.comparing(CowStatus::expectedDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        open.sort(Comparator.comparing((CowStatus c) -> priority(c.suggestion().action()))
                .thenComparing(c -> c.daysOpen() == null ? 0 : -c.daysOpen()));
        served.sort(Comparator.comparing(CowStatus::pdDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        int heatWatch = (int) all.stream().filter(r -> watchLevel(r.status(), today) != null).count();
        return new Overview(today, all.size(), heatWatch, calendar, new Groups(overdue, dueIn7, open, served, pregnant, dry));
    }

    @Transactional(readOnly = true)
    public List<HeatWatchItem> heatWatch(Long farmId, boolean showMoney) {
        LocalDate today = DailyDates.today();
        List<HeatWatchItem> out = new ArrayList<>();
        for (Result r : statuses(farmId, breedable(farmId, today), today, showMoney)) {
            String level = watchLevel(r.status(), today);
            if (level == null) {
                continue;
            }
            CowStatus s = r.status();
            String reason;
            int pct;
            if (level.equals("HIGH")) {
                long d = ChronoUnit.DAYS.between(today, s.nextHeatExpected());
                reason = (s.lastHeatDate() != null ? ChronoUnit.DAYS.between(s.lastHeatDate(), today) + " days since last heat. "
                        : "") + "Next cycle expected " + (d == 0 ? "today" : d > 0 ? "in " + d + " d" : (-d) + " d ago") + " ± 2 d.";
                pct = s.lastHeatDate() != null ? 85 : 70;
            } else if (level.equals("MEDIUM")) {
                reason = s.daysOpen() + " days since calving, " + (s.lastHeatDate() == null ? "no heat recorded"
                        : s.heatsMissed() + " heat(s) not seen") + ". Possible silent heat — consider a vet check.";
                pct = 55;
            } else {
                reason = "Heifer of service age, no heat recorded yet.";
                pct = 30;
            }
            out.add(new HeatWatchItem(s, level, pct, reason));
        }
        out.sort(Comparator.comparing(HeatWatchItem::confidencePct).reversed());
        return out;
    }

    @Transactional(readOnly = true)
    public History history(Long farmId, Long animalId, boolean showMoney) {
        LocalDate today = DailyDates.today();
        Animal a = animals.findByIdAndFarmId(animalId, farmId).orElseThrow(() -> ApiException.notFound("Animal not found."));
        if (a.getSex() != Sex.FEMALE) {
            throw ApiException.badRequest("Breeding history is for females.");
        }
        List<BreedingRecord> recs = records.findByFarmIdAndDamId(farmId, animalId);
        Result res = statuses(farmId, List.of(a), today, showMoney).get(0);
        CowStatus s = res.status();
        BigDecimal milk = sql.milkPerDay(farmId, List.of(animalId), today.minusDays(7), today).get(animalId);
        var settings = breeding.settings(farmId);

        // ---- this lactation, newest first
        List<HistoryEvent> ev = new ArrayList<>();
        LocalDate since = a.getLastCalvingDate();
        BreedingRecord calvedBy = recs.stream().filter(r -> since != null && since.equals(r.getCalvingDate())).findFirst().orElse(null);
        if (since != null) {
            String detail = "Lactation " + a.getLactationNumber() + " starts";
            if (calvedBy != null) {
                detail = calvedBy.getCalves().stream().map(c -> c.sex() == Sex.FEMALE ? "♀" : "♂").collect(Collectors.joining(" "))
                        + (calvedBy.getCalvingEase() == null ? "" : " · " + calvedBy.getCalvingEase().name().toLowerCase())
                        + (calvedBy.getCalvingOutcome() != Outcome.LIVE && calvedBy.getCalvingOutcome() != null
                        ? " · " + calvedBy.getCalvingOutcome().name().toLowerCase() : "") + " · " + detail;
            }
            ev.add(new HistoryEvent(since, "CALVING", "Calved", detail.replaceFirst("^ · ", ""), null,
                    calvedBy == null ? null : calvedBy.getId()));
        }
        List<FarmEvent> heats = events.findByFarmIdAndAnimalIdAndEventTypeOrderByEventDateDescIdDesc(farmId, animalId, EventType.HEAT);
        for (FarmEvent h : heats) {
            if (since != null && h.getEventDate().isBefore(since)) {
                continue;
            }
            int signs = h.getDetails().signs() == null ? 0 : h.getDetails().signs().size();
            ev.add(new HistoryEvent(h.getEventDate(), "HEAT", "Heat · " + (h.getSubType() == null ? "seen" : h.getSubType().toLowerCase()),
                    signs + (signs == 1 ? " sign" : " signs"), null, null));
        }
        res.missedHeatDates().forEach(d -> ev.add(new HistoryEvent(d, "HEAT_MISSED", "Heat expected — not seen", null, null, null)));
        Map<Long, Item> semen = items.findByFarmIdAndIdIn(farmId, recs.stream().map(BreedingRecord::getSemenItemId)
                .filter(Objects::nonNull).toList()).stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, Contact> cs = contacts.findByFarmIdAndIdIn(farmId, recs.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getTechnicianContactId(), r.getBullOwnerContactId()))
                .filter(Objects::nonNull).toList()).stream().collect(Collectors.toMap(Contact::getId, Function.identity()));
        Map<Long, Animal> sires = breeding.animalsById(farmId, recs.stream().map(BreedingRecord::getSireId).toList());
        int n = 0;
        for (BreedingRecord r : recs.stream().filter(r -> r.getServiceDate() != null && r.getLactationNumber() == a.getLactationNumber()
                && (since == null || !r.getServiceDate().isBefore(since)))
                .sorted(Comparator.comparing(BreedingRecord::getServiceDate)).toList()) {
            n++;
            String bull = r.getSemenItemId() != null && semen.containsKey(r.getSemenItemId())
                    ? semen.get(r.getSemenItemId()).getName() + (semen.get(r.getSemenItemId()).getDetails().breed() == null ? ""
                    : " · " + semen.get(r.getSemenItemId()).getDetails().breed())
                    : r.getSireId() != null && sires.containsKey(r.getSireId()) ? sires.get(r.getSireId()).getTag()
                    : r.getSireTag();
            String who = r.getTechnicianContactId() != null && cs.containsKey(r.getTechnicianContactId())
                    ? cs.get(r.getTechnicianContactId()).getName() : null;
            String title = (r.getMethod() == Method.NATURAL ? "Natural service" : r.getMethod().name()) + " #" + n
                    + (res.failedServiceIds().contains(r.getId()) ? " — failed" : "");
            BigDecimal cost = sum(r.getSemenCost(), r.getTechnicianFee(), r.getBullFee());
            ev.add(new HistoryEvent(r.getServiceDate(), "SERVICE", title,
                    java.util.stream.Stream.of(bull, who).filter(Objects::nonNull).collect(Collectors.joining(" · ")),
                    showMoney ? cost : null, r.getId()));
            if (r.getPregCheckDate() != null) {
                ev.add(new HistoryEvent(r.getPregCheckDate(), "PD", "Pregnancy check: "
                        + (r.getPregnancyStatus() == Pregnancy.PREGNANT ? "pregnant" : r.getPregnancyStatus() == Pregnancy.NOT_PREGNANT
                        ? "not pregnant" : "unclear"),
                        r.getPregCheckMethod() == null ? null : r.getPregCheckMethod().name().toLowerCase(),
                        showMoney ? r.getPregCheckCost() : null, r.getId()));
            }
        }
        if (s.dryOffDate() != null && a.getCategory() != Category.DRY) {
            ev.add(new HistoryEvent(s.dryOffDate(), "DRY_OFF", "Dry off", "due " + s.expectedDueDate(), null, s.currentServiceId()));
        }
        if (s.nextHeatExpected() != null && (s.status() == BreedingViews.Status.OPEN
                || s.status() == BreedingViews.Status.HEIFER_NOT_SERVED)) {
            ev.add(new HistoryEvent(s.nextHeatExpected(), "EXPECTED_HEAT", "Next heat expected", null, null, null));
        }
        ev.sort(Comparator.comparing(HistoryEvent::date).reversed());

        // ---- earlier lactations
        Map<Integer, List<BreedingRecord>> byLac = recs.stream().collect(Collectors.groupingBy(BreedingRecord::getLactationNumber));
        Map<Integer, LocalDate> calvedOn = new HashMap<>();
        for (BreedingRecord r : recs) {
            if (r.getCalvingDate() != null && r.getCalvingOutcome() != Outcome.ABORTED) {
                calvedOn.put(r.getLactationNumber() + 1, r.getCalvingDate());
            }
        }
        if (since != null) {
            calvedOn.putIfAbsent(a.getLactationNumber(), since);
        }
        List<Lactation> lactations = new ArrayList<>();
        int conceptions = 0, servicesConceived = 0, calvings = 0;
        long daysOpenSum = 0;
        int daysOpenCount = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (BreedingRecord r : recs) {
            total = total.add(r.totalCost());
            if (r.getCalvingDate() != null && r.getCalvingOutcome() != Outcome.ABORTED) {
                calvings++;
            }
        }
        for (int k = a.getLactationNumber() - 1; k >= 0; k--) {
            List<BreedingRecord> lr = byLac.getOrDefault(k, List.of());
            LocalDate from = calvedOn.get(k);
            if (lr.isEmpty() && from == null) {
                continue;
            }
            int services = (int) lr.stream().filter(r -> r.getServiceDate() != null).count();
            BreedingRecord conceivedBy = lr.stream().filter(r -> r.getCalvingDate() != null && r.getCalvingOutcome() != Outcome.ABORTED)
                    .findFirst().orElse(null);
            LocalDate next = conceivedBy == null ? null : conceivedBy.getCalvingDate();
            Integer daysOpen = from != null && conceivedBy != null && conceivedBy.getServiceDate() != null
                    ? (int) ChronoUnit.DAYS.between(from, conceivedBy.getServiceDate()) : null;
            Integer interval = from != null && next != null ? (int) ChronoUnit.DAYS.between(from, next) : null;
            BigDecimal spend = lr.stream().map(BreedingRecord::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (conceivedBy != null && services > 0) {
                conceptions++;
                servicesConceived += services;
            }
            if (daysOpen != null) {
                daysOpenSum += daysOpen;
                daysOpenCount++;
            }
            lactations.add(new Lactation(k, from, next, services, daysOpen, interval, showMoney ? spend : null, conceivedBy != null));
        }
        Totals totals = new Totals(daysOpenCount == 0 ? null : (int) Math.round((double) daysOpenSum / daysOpenCount),
                conceptions == 0 ? null : BigDecimal.valueOf(servicesConceived).divide(BigDecimal.valueOf(conceptions), 1, RoundingMode.HALF_UP),
                calvings, showMoney ? total : null);
        return new History(s, milk, new Current(a.getLactationNumber(), since, ev), lactations, totals);
    }

    // ---------- shared ----------

    /** Active females that can be bred now or soon: heifers near service age, milking and dry cows. */
    List<Animal> breedable(Long farmId, LocalDate today) {
        var s = breedingSettingsMonths(farmId);
        return animals.findByFarmIdOrderByTagAsc(farmId).stream()
                .filter(a -> a.getStatus() == Status.ACTIVE && a.getSex() == Sex.FEMALE)
                .filter(a -> a.getCategory() == Category.MILKING || a.getCategory() == Category.DRY
                        || a.getCategory() == Category.HEIFER && (a.getDateOfBirth() == null
                        || !a.getDateOfBirth().plusMonths(Math.max(0, s - 1)).isAfter(today)))
                .toList();
    }

    private int breedingSettingsMonths(Long farmId) {
        return breeding.settings(farmId).heiferFirstServiceMonths();
    }

    List<Result> statuses(Long farmId, List<Animal> cows, LocalDate today, boolean showMoney) {
        if (cows.isEmpty()) {
            return List.of();
        }
        var settings = breeding.settings(farmId);
        List<Long> ids = cows.stream().map(Animal::getId).toList();
        Map<Long, List<LocalDate>> heats = sql.heatDates(farmId, ids);
        Map<Long, List<Svc>> svcs = new HashMap<>();
        for (BreedingRecord r : records.findByFarmIdAndDamIdIn(farmId, ids)) {
            if (r.getServiceDate() != null) {
                svcs.computeIfAbsent(r.getDamId(), k -> new ArrayList<>()).add(new Svc(r.getId(), r.getServiceDate(),
                        r.getPregnancyStatus(), r.getCalvingDate(), r.getExpectedDueDate(), r.getLactationNumber(), r.totalCost()));
            }
        }
        Map<Long, BigDecimal> milk = sql.milkPerDay(farmId, ids, today.minusDays(7), today);
        List<Result> out = new ArrayList<>();
        for (Animal a : cows) {
            Cow cow = new Cow(a.getId(), a.getTag(), a.getName(), a.getBreed(), a.getCategory(), a.getDateOfBirth(),
                    a.getLastCalvingDate(), a.getLactationNumber(), a.getCurrentWeightKg(), milk.get(a.getId()));
            out.add(BreedingStatusCalc.compute(settings, cow, heats.getOrDefault(a.getId(), List.of()),
                    svcs.getOrDefault(a.getId(), List.of()), today, showMoney));
        }
        return out;
    }

    static String watchLevel(CowStatus s, LocalDate today) {
        if (s.status() != BreedingViews.Status.OPEN && s.status() != BreedingViews.Status.HEIFER_NOT_SERVED) {
            return null;
        }
        if (s.nextHeatExpected() != null && Math.abs(ChronoUnit.DAYS.between(today, s.nextHeatExpected())) <= 2) {
            return "HIGH";
        }
        if (s.status() == BreedingViews.Status.OPEN && s.daysOpen() != null && s.heatsExpected() > 0
                && (s.lastHeatDate() == null || s.heatsMissed() > 0)) {
            return "MEDIUM";
        }
        if (s.status() == BreedingViews.Status.HEIFER_NOT_SERVED
                && s.suggestion().action() == BreedingViews.Action.FIRST_SERVICE_DUE) {
            return "LOW";
        }
        return null;
    }

    private static int priority(BreedingViews.Action a) {
        return switch (a) {
            case CONSIDER_SELLING -> 0;
            case VET_CHECK -> 1;
            case HEAT_DUE -> 2;
            case FIRST_SERVICE_DUE -> 3;
            default -> 4;
        };
    }

    private static void addDay(List<CalendarItem> cal, LocalDate from, LocalDate to, LocalDate d, String kind, CowStatus s) {
        if (d != null && !d.isBefore(from) && !d.isAfter(to)) {
            cal.add(new CalendarItem(d, kind, s.animal()));
        }
    }

    private static BigDecimal sum(BigDecimal... values) {
        BigDecimal t = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            if (v != null) {
                t = t.add(v);
            }
        }
        return t;
    }
}
