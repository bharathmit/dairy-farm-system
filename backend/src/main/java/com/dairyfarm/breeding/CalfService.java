package com.dairyfarm.breeding;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.animal.Animal;
import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.animal.AnimalEnums.Status;
import com.dairyfarm.animal.AnimalRepository;
import com.dairyfarm.breeding.BreedingSettings.TaperStep;
import com.dairyfarm.breeding.BreedingViews.Badge;
import com.dairyfarm.breeding.BreedingViews.CalfFeeding;
import com.dairyfarm.breeding.BreedingViews.CalfItem;
import com.dairyfarm.breeding.BreedingViews.CalfList;
import com.dairyfarm.breeding.BreedingViews.Colostrum;
import com.dairyfarm.breeding.BreedingViews.Counts;
import com.dairyfarm.breeding.BreedingViews.FeedingDay;
import com.dairyfarm.breeding.BreedingViews.FeedingRequest;
import com.dairyfarm.breeding.BreedingViews.FeedingSheet;
import com.dairyfarm.breeding.BreedingViews.FeedingSheetRequest;
import com.dairyfarm.breeding.BreedingViews.MaleSell;
import com.dairyfarm.breeding.BreedingViews.Session;
import com.dairyfarm.breeding.BreedingViews.SheetCalf;
import com.dairyfarm.breeding.BreedingViews.SheetEntry;
import com.dairyfarm.breeding.BreedingViews.Weaning;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.common.Sex;
import com.dairyfarm.farm.FarmService;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.record.FarmEvent;
import com.dairyfarm.record.FarmEvent.Details;
import com.dairyfarm.record.FarmEvent.EventType;
import com.dairyfarm.record.FarmEventRepository;

/** Calf list, colostrum, weaning and male sell window, and daily feeding (one calf or a group). */
@Service
public class CalfService {

    /** Calves this young get colostrum rather than milk. */
    static final int COLOSTRUM_DAYS = 3;

    private final AnimalRepository animals;
    private final FarmEventRepository events;
    private final BreedingRecordRepository records;
    private final BreedingService breeding;

    public CalfService(AnimalRepository animals, FarmEventRepository events, BreedingRecordRepository records,
                       BreedingService breeding) {
        this.animals = animals;
        this.events = events;
        this.records = records;
        this.breeding = breeding;
    }

    @Transactional(readOnly = true)
    public CalfList list(Long farmId, Sex sex, Integer minDays, Integer maxDays) {
        LocalDate today = DailyDates.today();
        List<Animal> all = calves(farmId);
        List<Animal> shown = all.stream().filter(a -> sex == null || a.getSex() == sex)
                .filter(a -> inAge(age(a, today), minDays, maxDays)).toList();
        List<CalfItem> items = items(farmId, shown, today);
        int needAction = (int) items.stream().filter(i -> i.badges().stream()
                .anyMatch(b -> b.tone().equals("danger") || b.tone().equals("warn"))).count();
        BigDecimal milkToday = BigDecimal.ZERO;
        for (FarmEvent e : feedings(farmId, all.stream().map(Animal::getId).toList(), today, today)) {
            milkToday = milkToday.add(milk(e.getDetails()));
        }
        int female = (int) all.stream().filter(a -> a.getSex() == Sex.FEMALE).count();
        return new CalfList(new Counts(all.size(), female, all.size() - female, shown.size()), milkToday, needAction, items);
    }

    @Transactional(readOnly = true)
    public FeedingDay feeding(Long farmId, Long animalId, LocalDate date) {
        Animal calf = calf(farmId, animalId);
        LocalDate d = date == null ? DailyDates.today() : date;
        return day(farmId, calf, d);
    }

    @Transactional
    public FeedingDay saveFeeding(Long farmId, Long animalId, LocalDate date, FeedingRequest req) {
        Animal calf = calf(farmId, animalId);
        DailyDates.notFuture(date);
        if (calf.getDateOfBirth() != null && date.isBefore(calf.getDateOfBirth())) {
            throw ApiException.badRequest("The date is before the calf was born.");
        }
        FarmEvent e = find(farmId, animalId, date);
        boolean empty = req.milkAm() == null && req.milkPm() == null && req.starterKg() == null && req.waterL() == null
                && req.colostrumLitres() == null && blank(req.notes()) == null;
        if (empty) {
            if (e != null) {
                events.delete(e);
                events.flush();
            }
            return day(farmId, calf, date);
        }
        if (e == null) {
            e = newFeeding(farmId, animalId, date);
        }
        e.setDetails(Details.calfFeeding(req.milkAm(), req.milkPm(), req.starterKg(), req.waterL(), req.colostrumLitres(),
                req.colostrumAt() == null ? null : req.colostrumAt().toString()));
        e.setNotes(blank(req.notes()));
        events.saveAndFlush(e);
        return day(farmId, calf, date);
    }

    @Transactional(readOnly = true)
    public FeedingSheet sheet(Long farmId, LocalDate date, Session session, Sex sex, Integer minDays, Integer maxDays,
                              Long groupId) {
        LocalDate d = date == null ? DailyDates.today() : date;
        var s = breeding.settings(farmId);
        List<Animal> list = calves(farmId).stream().filter(a -> sex == null || a.getSex() == sex)
                .filter(a -> groupId == null || groupId.equals(a.getGroupId()))
                .filter(a -> inAge(age(a, d), minDays, maxDays))
                .filter(a -> a.getDateOfBirth() == null || !a.getDateOfBirth().isAfter(d))
                .toList();
        Map<Long, FarmEvent> saved = feedings(farmId, list.stream().map(Animal::getId).toList(), d, d).stream()
                .collect(Collectors.toMap(FarmEvent::getAnimalId, Function.identity(), (a, b) -> a));
        List<SheetCalf> rows = new ArrayList<>();
        for (Animal a : list) {
            Integer age = age(a, d);
            boolean colostrum = age != null && age < COLOSTRUM_DAYS;
            TaperStep plan = s.taperFor(age);
            FarmEvent e = saved.get(a.getId());
            Details det = e == null ? null : e.getDetails();
            BigDecimal litres = det == null ? null : session == Session.AM ? det.milkAm() : det.milkPm();
            rows.add(new SheetCalf(BreedingService.ref(a), a.getSex(), age,
                    plan.milkLitres().divide(BigDecimal.valueOf(2), 1, java.math.RoundingMode.HALF_UP),
                    litres, det == null ? null : det.starterKg(), colostrum));
        }
        return new FeedingSheet(d, session, rows);
    }

    @Transactional
    public FeedingSheet saveSheet(Long farmId, FeedingSheetRequest req) {
        DailyDates.notFuture(req.date());
        Set<Long> ids = new HashSet<>();
        for (SheetEntry en : req.entries()) {
            if (!ids.add(en.animalId())) {
                throw ApiException.badRequest("A calf is listed twice.");
            }
        }
        Map<Long, Animal> calves = calves(farmId).stream().collect(Collectors.toMap(Animal::getId, Function.identity()));
        for (Long id : ids) {
            if (!calves.containsKey(id)) {
                throw ApiException.badRequest("Animal " + id + " isn't an active calf on this farm.");
            }
        }
        Map<Long, FarmEvent> saved = feedings(farmId, ids, req.date(), req.date()).stream()
                .collect(Collectors.toMap(FarmEvent::getAnimalId, Function.identity(), (a, b) -> a));
        UUID batch = UUID.randomUUID();
        for (SheetEntry en : req.entries()) {
            Animal a = calves.get(en.animalId());
            FarmEvent e = saved.get(a.getId());
            if (e == null) {
                if (en.litres() == null && en.starterKg() == null) {
                    continue;
                }
                e = newFeeding(farmId, a.getId(), req.date());
            }
            Details d = e.getDetails();
            BigDecimal am = req.session() == Session.AM ? en.litres() : d.milkAm();
            BigDecimal pm = req.session() == Session.PM ? en.litres() : d.milkPm();
            BigDecimal starter = en.starterKg() != null ? en.starterKg() : d.starterKg();
            Integer age = age(a, req.date());
            BigDecimal colostrum = d.colostrumLitres();
            if (age != null && age < COLOSTRUM_DAYS) {
                colostrum = (am == null && pm == null) ? null : nz(am).add(nz(pm));
            }
            e.setDetails(Details.calfFeeding(am, pm, starter, d.waterL(), colostrum, d.colostrumAt()));
            e.setBatchId(batch);
            if (am == null && pm == null && starter == null && d.waterL() == null && colostrum == null && e.getNotes() == null) {
                if (e.getId() != null) {
                    events.delete(e);
                }
                continue;
            }
            events.save(e);
        }
        events.flush();
        return sheet(farmId, req.date(), req.session(), null, null, null, null).filterTo(ids);
    }

    // ---------- helpers ----------

    private FeedingDay day(Long farmId, Animal calf, LocalDate date) {
        var s = breeding.settings(farmId);
        LocalDate today = DailyDates.today();
        CalfItem item = items(farmId, List.of(calf), today).get(0);
        List<FarmEvent> recent = events.findByFarmIdAndAnimalIdAndEventTypeOrderByEventDateDescIdDesc(farmId, calf.getId(),
                EventType.CALF_FEEDING);
        FarmEvent on = recent.stream().filter(e -> e.getEventDate().equals(date)).findFirst().orElse(null);
        return new FeedingDay(item, date, on == null ? null : view(on), s.taperFor(age(calf, date)), s.calfTaper(),
                recent.stream().limit(7).map(CalfService::view).toList());
    }

    private List<CalfItem> items(Long farmId, List<Animal> calves, LocalDate today) {
        if (calves.isEmpty()) {
            return List.of();
        }
        var s = breeding.settings(farmId);
        List<Long> ids = calves.stream().map(Animal::getId).toList();
        Map<Long, List<FarmEvent>> feeds = new HashMap<>();
        for (FarmEvent e : feedings(farmId, ids, today.minusDays(30), today)) {
            feeds.computeIfAbsent(e.getAnimalId(), k -> new ArrayList<>()).add(e);
        }
        feeds.values().forEach(l -> l.sort(Comparator.comparing(FarmEvent::getEventDate).reversed()));
        Map<Long, Animal> dams = breeding.animalsById(farmId, calves.stream().map(Animal::getDamId).filter(Objects::nonNull).toList());
        Map<Long, BreedingRecord> birth = new HashMap<>();
        Set<Long> damIds = dams.keySet();
        if (!damIds.isEmpty()) {
            for (BreedingRecord r : records.findByFarmIdAndDamIdIn(farmId, damIds)) {
                r.getCalves().forEach(c -> {
                    if (c.animalId() != null) {
                        birth.put(c.animalId(), r);
                    }
                });
            }
        }
        List<CalfItem> out = new ArrayList<>();
        for (Animal a : calves) {
            Integer age = age(a, today);
            List<FarmEvent> fs = feeds.getOrDefault(a.getId(), List.of());
            FarmEvent last = fs.isEmpty() ? null : fs.get(0);
            BreedingRecord br = birth.get(a.getId());
            boolean given = fs.stream().anyMatch(e -> e.getDetails().colostrumLitres() != null
                    && e.getDetails().colostrumLitres().signum() > 0) || br != null && br.getColostrumGivenAt() != null;
            Instant dueBy = null;
            if (br != null && br.getCalvingDate() != null) {
                LocalTime t = br.getCalvingTime() != null ? br.getCalvingTime() : LocalTime.of(6, 0);
                dueBy = br.getCalvingDate().atTime(t).plusHours(s.colostrumHours()).atZone(FarmService.FARM_ZONE).toInstant();
            } else if (a.getDateOfBirth() != null) {
                dueBy = a.getDateOfBirth().atTime(LocalTime.of(6, 0)).plusHours(s.colostrumHours()).atZone(FarmService.FARM_ZONE).toInstant();
            }
            List<Badge> badges = new ArrayList<>();
            if (age != null && age < COLOSTRUM_DAYS && !given) {
                badges.add(new Badge("Colostrum due", "danger"));
            } else if (given && age != null && age <= 7) {
                badges.add(new Badge("Colostrum ✓", "ok"));
            }
            Weaning weaning = null;
            if (age != null && age >= s.weanMinDays() - 14) {
                int inDays = Math.max(0, s.weanMinDays() - age);
                boolean starterOk = fs.size() >= s.weanStarterDays();
                for (int k = 0; k < s.weanStarterDays() && starterOk; k++) {
                    FarmEvent e = fs.get(k);
                    starterOk = e.getEventDate().equals(today.minusDays(k)) && e.getDetails().starterKg() != null
                            && e.getDetails().starterKg().compareTo(s.weanStarterKg()) >= 0;
                }
                boolean ready = inDays == 0 && starterOk;
                weaning = new Weaning(ready, inDays);
                badges.add(ready ? new Badge("Weaning ready", "info")
                        : new Badge(inDays == 0 ? "Wean when on " + s.weanStarterKg().stripTrailingZeros().toPlainString()
                        + " kg starter" : "Weaning in " + inDays + " d", "info"));
            }
            MaleSell sell = null;
            if (a.getSex() == Sex.MALE && age != null && age <= s.maleSellToDay()) {
                int left = s.maleSellToDay() - age;
                sell = new MaleSell(s.maleSellFromDay(), s.maleSellToDay(), left);
                badges.add(new Badge("Male · sell window " + s.maleSellFromDay() + "–" + s.maleSellToDay() + " d",
                        age >= s.maleSellFromDay() && left <= 14 ? "warn" : "info"));
            }
            Animal dam = a.getDamId() == null ? null : dams.get(a.getDamId());
            out.add(new CalfItem(BreedingService.ref(a), a.getSex(), a.getBreed(), BreedingService.ref(dam), age,
                    a.getCurrentWeightKg() != null ? a.getCurrentWeightKg() : a.getBirthWeightKg(),
                    new Colostrum(given, dueBy), last == null ? null : milkOrNull(last.getDetails()),
                    last == null ? null : last.getDetails().starterKg(), s.taperFor(age), null, weaning, sell, List.of(), badges));
        }
        out.sort(Comparator.comparing((CalfItem c) -> c.ageDays() == null ? Integer.MAX_VALUE : c.ageDays()));
        return out;
    }

    private List<Animal> calves(Long farmId) {
        return animals.findByFarmIdOrderByTagAsc(farmId).stream()
                .filter(a -> a.getStatus() == Status.ACTIVE && a.getCategory() == Category.CALF).toList();
    }

    private Animal calf(Long farmId, Long id) {
        Animal a = animals.findByIdAndFarmId(id, farmId).orElseThrow(() -> ApiException.notFound("Animal not found."));
        if (a.getStatus() != Status.ACTIVE || a.getCategory() != Category.CALF) {
            throw ApiException.badRequest(a.getTag() + " isn't an active calf.");
        }
        return a;
    }

    private List<FarmEvent> feedings(Long farmId, Collection<Long> ids, LocalDate from, LocalDate to) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return events.findAll((root, q, cb) -> cb.and(cb.equal(root.get("farmId"), farmId),
                cb.equal(root.get("eventType"), EventType.CALF_FEEDING), root.get("animalId").in(ids),
                cb.between(root.get("eventDate"), from, to)), Sort.by("eventDate"));
    }

    private FarmEvent find(Long farmId, Long animalId, LocalDate date) {
        return feedings(farmId, List.of(animalId), date, date).stream().findFirst().orElse(null);
    }

    private static FarmEvent newFeeding(Long farmId, Long animalId, LocalDate date) {
        FarmEvent e = new FarmEvent(farmId, EventType.CALF_FEEDING);
        e.setAnimalId(animalId);
        e.setEventDate(date);
        return e;
    }

    private static CalfFeeding view(FarmEvent e) {
        Details d = e.getDetails();
        return new CalfFeeding(e.getEventDate(), d.milkAm(), d.milkPm(), d.starterKg(), d.waterL(), d.colostrumLitres(),
                d.colostrumAt() == null ? null : LocalTime.parse(d.colostrumAt()), e.getNotes());
    }

    private static BigDecimal milk(Details d) {
        BigDecimal m = nz(d.milkAm()).add(nz(d.milkPm()));
        return m.signum() == 0 && d.colostrumLitres() != null ? d.colostrumLitres() : m;
    }

    private static BigDecimal milkOrNull(Details d) {
        return d.milkAm() == null && d.milkPm() == null && d.colostrumLitres() == null ? null : milk(d);
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    static Integer age(Animal a, LocalDate on) {
        return a.getDateOfBirth() == null ? null : (int) ChronoUnit.DAYS.between(a.getDateOfBirth(), on);
    }

    private static boolean inAge(Integer age, Integer min, Integer max) {
        if (min == null && max == null) {
            return true;
        }
        return age != null && (min == null || age >= min) && (max == null || age <= max);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
