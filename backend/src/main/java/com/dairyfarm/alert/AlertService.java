package com.dairyfarm.alert;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.alert.AlertRepository.Details;
import com.dairyfarm.alert.AlertRepository.Row;
import com.dairyfarm.alert.AlertViews.Action;
import com.dairyfarm.alert.AlertViews.Alert;
import com.dairyfarm.alert.AlertViews.AlertAnimal;
import com.dairyfarm.alert.AlertViews.AlertCategory;
import com.dairyfarm.alert.AlertViews.AlertList;
import com.dairyfarm.alert.AlertViews.AlertStatus;
import com.dairyfarm.alert.AlertViews.Counts;
import com.dairyfarm.alert.AlertViews.MemberRef;
import com.dairyfarm.alert.AlertViews.ReminderRequest;
import com.dairyfarm.alert.AlertViews.ReminderType;
import com.dairyfarm.alert.AlertViews.Repeat;
import com.dairyfarm.alert.AlertViews.Rule;
import com.dairyfarm.alert.AlertViews.RuleUpdate;
import com.dairyfarm.alert.AlertViews.Severity;
import com.dairyfarm.alert.AlertViews.Source;
import com.dairyfarm.animal.AnimalRepository;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.farm.Farm;
import com.dairyfarm.farm.FarmRepository;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.security.CurrentUser;
import com.dairyfarm.security.FarmContext;

/** Reading and acting on alerts and reminders, and editing the alert rules. */
@Service
public class AlertService {

    private final AlertRepository repo;
    private final AlertEngine engine;
    private final AlertRuleBook book;
    private final FarmRepository farms;
    private final AnimalRepository animals;
    private final FarmContext ctx;

    public AlertService(AlertRepository repo, AlertEngine engine, AlertRuleBook book, FarmRepository farms,
                        AnimalRepository animals, FarmContext ctx) {
        this.repo = repo;
        this.engine = engine;
        this.book = book;
        this.farms = farms;
        this.animals = animals;
        this.ctx = ctx;
    }

    /** Open alerts the caller may see, snoozed ones hidden until their time. */
    @Transactional
    public List<Alert> visibleOpen(Long farmId, boolean owner) {
        engine.refreshIfStale(farmId);
        LocalDate today = DailyDates.today();
        Instant now = Instant.now();
        return repo.open(farmId).stream()
                .filter(r -> owner || r.workerVisible())
                .filter(r -> r.snoozedUntil() == null || !r.snoozedUntil().isAfter(now))
                .map(r -> view(r, today))
                .toList();
    }

    @Transactional
    public AlertList list(Long farmId, boolean owner, AlertCategory category, Severity severity, AlertStatus status,
                          Long animalId) {
        LocalDate today = DailyDates.today();
        List<Alert> base;
        if (status == null || status == AlertStatus.OPEN) {
            base = visibleOpen(farmId, owner);
        } else {
            base = repo.byStatus(farmId, status, 200).stream().filter(r -> owner || r.workerVisible())
                    .map(r -> view(r, today)).toList();
        }
        if (animalId != null) {
            base = base.stream().filter(a -> a.animal() != null && animalId.equals(a.animal().id())).toList();
        }
        Map<AlertCategory, Integer> byCat = new EnumMap<>(AlertCategory.class);
        Map<Severity, Integer> bySev = new EnumMap<>(Severity.class);
        int overdue = 0;
        for (Alert a : base) {
            byCat.merge(a.category(), 1, Integer::sum);
            bySev.merge(a.severity(), 1, Integer::sum);
            if (a.overdueDays() != null && a.overdueDays() > 0) {
                overdue++;
            }
        }
        Counts counts = new Counts(base.size(), byCat, bySev, overdue);
        List<Alert> shown = base.stream()
                .filter(a -> category == null || a.category() == category)
                .filter(a -> severity == null || a.severity() == severity)
                .sorted(Comparator.comparing(Alert::dueDate).thenComparing(a -> a.severity().ordinal())
                        .thenComparing(Alert::id))
                .toList();
        List<Alert> o = new ArrayList<>(), t = new ArrayList<>(), w = new ArrayList<>(), l = new ArrayList<>();
        for (Alert a : shown) {
            if (a.dueDate().isBefore(today)) {
                o.add(a);
            } else if (a.dueDate().isEqual(today)) {
                t.add(a);
            } else if (!a.dueDate().isAfter(today.plusDays(7))) {
                w.add(a);
            } else {
                l.add(a);
            }
        }
        o.sort(Comparator.comparing((Alert a) -> a.severity().ordinal()).thenComparing(Alert::dueDate));
        t.sort(Comparator.comparing((Alert a) -> a.severity().ordinal()).thenComparing(Alert::id));
        return new AlertList(counts, o, t, w, l);
    }

    @Transactional(readOnly = true)
    public Alert get(Long farmId, Long id, boolean owner) {
        return view(load(farmId, id, owner), DailyDates.today());
    }

    @Transactional
    public Alert done(Long farmId, Long id, boolean owner) {
        Row r = load(farmId, id, owner);
        if (r.status() != AlertStatus.OPEN) {
            return view(r, DailyDates.today());
        }
        repo.setStatus(id, AlertStatus.DONE, ctx.memberId());
        if (r.source() == Source.REMINDER && r.repeat() != Repeat.NONE) {
            LocalDate next = switch (r.repeat()) {
                case DAILY -> r.dueDate().plusDays(1);
                case WEEKLY -> r.dueDate().plusWeeks(1);
                case MONTHLY -> r.dueDate().plusMonths(1);
                case NONE -> r.dueDate();
            };
            LocalDate today = DailyDates.today();
            while (next.isBefore(today)) {
                next = switch (r.repeat()) {
                    case DAILY -> next.plusDays(1);
                    case WEEKLY -> next.plusWeeks(1);
                    default -> next.plusMonths(1);
                };
            }
            repo.insertReminder(farmId, r.title(), r.details(), next, r.dueTime(),
                    r.reminderType() == null ? ReminderType.OTHER : r.reminderType(), r.animalId(), r.workerVisible(),
                    r.repeat(), r.createdBy());
        }
        return view(load(farmId, id, owner), DailyDates.today());
    }

    @Transactional
    public Alert dismiss(Long farmId, Long id, boolean owner) {
        Row r = load(farmId, id, owner);
        if (r.source() == Source.RULE && !owner) {
            throw ApiException.forbidden("Only farm owners can dismiss alerts. Mark it done instead.");
        }
        repo.setStatus(id, AlertStatus.DISMISSED, ctx.memberId());
        return view(load(farmId, id, owner), DailyDates.today());
    }

    @Transactional
    public Alert snooze(Long farmId, Long id, Instant until, boolean owner) {
        Row r = load(farmId, id, owner);
        Instant now = Instant.now();
        if (!until.isAfter(now)) {
            throw ApiException.badRequest("Pick a time in the future.");
        }
        if (until.isAfter(now.plus(31, ChronoUnit.DAYS))) {
            throw ApiException.badRequest("Snooze for up to 30 days.");
        }
        if (r.status() != AlertStatus.OPEN) {
            throw ApiException.badRequest("Only open alerts can be snoozed.");
        }
        repo.snooze(id, until);
        return view(load(farmId, id, owner), DailyDates.today());
    }

    @Transactional
    public AlertViews.RefreshResult refresh(Long farmId) {
        return engine.refresh(farmId);
    }

    // ---------- reminders ----------

    @Transactional
    public Alert createReminder(Long farmId, ReminderRequest req, boolean owner) {
        checkAnimal(farmId, req.animalId());
        Long id = repo.insertReminder(farmId, req.title().trim(), new Details(null, List.of(), Action.NONE, blank(req.notes())),
                req.date(), req.time(), req.type() == null ? ReminderType.OTHER : req.type(), req.animalId(),
                req.workerVisible() == null || req.workerVisible(), req.repeat() == null ? Repeat.NONE : req.repeat(),
                CurrentUser.idOrNull());
        return get(farmId, id, owner);
    }

    @Transactional
    public Alert updateReminder(Long farmId, Long id, ReminderRequest req, boolean owner) {
        Row r = reminder(farmId, id, owner);
        checkAnimal(farmId, req.animalId());
        repo.updateReminder(r.id(), req.title().trim(), new Details(null, List.of(), Action.NONE, blank(req.notes())),
                req.date(), req.time(), req.type() == null ? ReminderType.OTHER : req.type(), req.animalId(),
                req.workerVisible() == null || req.workerVisible(), req.repeat() == null ? Repeat.NONE : req.repeat());
        return get(farmId, id, owner);
    }

    @Transactional
    public void deleteReminder(Long farmId, Long id, boolean owner) {
        repo.delete(reminder(farmId, id, owner).id());
    }

    private Row reminder(Long farmId, Long id, boolean owner) {
        Row r = load(farmId, id, owner);
        if (r.source() != Source.REMINDER) {
            throw ApiException.badRequest("Only reminders can be edited. Change the alert rule instead.");
        }
        UUID me = CurrentUser.idOrNull();
        if (!owner && (r.createdBy() == null || !r.createdBy().equals(me))) {
            throw ApiException.forbidden("Only the owner or the person who added it can change this reminder.");
        }
        return r;
    }

    private void checkAnimal(Long farmId, Long animalId) {
        if (animalId != null && animals.findByIdAndFarmId(animalId, farmId).isEmpty()) {
            throw ApiException.badRequest("That animal isn't on this farm.");
        }
    }

    // ---------- rules ----------

    @Transactional(readOnly = true)
    public List<Rule> rules(Long farmId) {
        return book.view(farm(farmId));
    }

    @Transactional
    public List<Rule> saveRules(Long farmId, List<RuleUpdate> updates) {
        Farm farm = farm(farmId);
        book.apply(farm, updates);
        farms.saveAndFlush(farm);
        engine.refresh(farmId);
        return book.view(farm);
    }

    private Farm farm(Long farmId) {
        return farms.findById(farmId).orElseThrow(() -> ApiException.notFound("Farm not found."));
    }

    // ---------- views ----------

    private Row load(Long farmId, Long id, boolean owner) {
        Row r = repo.find(farmId, id).orElseThrow(() -> ApiException.notFound("Alert not found."));
        if (!owner && !r.workerVisible()) {
            throw ApiException.notFound("Alert not found.");
        }
        return r;
    }

    static Alert view(Row r, LocalDate today) {
        Integer overdue = r.status() == AlertStatus.OPEN && r.dueDate().isBefore(today)
                ? (int) ChronoUnit.DAYS.between(r.dueDate(), today) : null;
        Details d = r.details() == null ? Details.EMPTY : r.details();
        return new Alert(r.id(), r.source(), r.ruleCode(), r.category(), r.severity(), r.title(), r.detail(),
                r.animalId() == null ? null : new AlertAnimal(r.animalId(), r.animalTag(), r.animalName(), r.animalCategory(),
                        r.animalBreed()),
                r.dueDate(), r.dueTime(), overdue, r.status(), r.snoozedUntil(), r.workerVisible(), r.repeat(), r.reminderType(),
                d.why(), d.facts() == null ? List.of() : d.facts(), d.action() == null ? Action.NONE : d.action(),
                r.doneBy() == null ? null : new MemberRef(r.doneBy(), r.doneByName()), r.doneAt(), r.autoClosed(), d.notes());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
