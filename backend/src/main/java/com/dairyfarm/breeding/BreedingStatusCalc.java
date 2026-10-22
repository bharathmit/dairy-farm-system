package com.dairyfarm.breeding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.breeding.BreedingRecord.Pregnancy;
import com.dairyfarm.breeding.BreedingViews.Action;
import com.dairyfarm.breeding.BreedingViews.AnimalRef;
import com.dairyfarm.breeding.BreedingViews.CowStatus;
import com.dairyfarm.breeding.BreedingViews.Status;
import com.dairyfarm.breeding.BreedingViews.Suggestion;

/**
 * Works out a female's breeding status, days open, missed heats, failed services and the keep-or-sell suggestion.
 * Pure calculation: everything it needs is passed in.
 */
public final class BreedingStatusCalc {

    /** Days after a service that a new heat or service means the service failed. */
    static final int RETURN_DAYS = 18;
    /** A heat seen within this many days of the expected date counts for that cycle. */
    static final int WINDOW = 3;

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    public record Cow(Long id, String tag, String name, String breed, Category category, LocalDate dateOfBirth,
                      LocalDate lastCalvingDate, int lactationNumber, BigDecimal weightKg, BigDecimal milkPerDay) {
    }

    /** One service (breeding record with a service date). */
    public record Svc(Long id, LocalDate date, Pregnancy pregnancy, LocalDate calvingDate, LocalDate expectedDue,
                      int lactationNumber, BigDecimal cost) {
    }

    public record Result(CowStatus status, List<LocalDate> missedHeatDates, Set<Long> failedServiceIds,
                         List<Svc> servicesThisLactation) {
    }

    private BreedingStatusCalc() {
    }

    public static Result compute(BreedingSettings settings, Cow cow, List<LocalDate> heatDates, List<Svc> services,
                                 LocalDate today, boolean showMoney) {
        var s = settings.resolved();
        int cycle = s.heatCycleDays();
        LocalDate since = cow.lastCalvingDate();
        boolean heifer = since == null && cow.lactationNumber() == 0;

        List<Svc> lac = services.stream()
                .filter(v -> v.date() != null && v.lactationNumber() == cow.lactationNumber()
                        && (since == null || !v.date().isBefore(since)))
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .toList();
        TreeSet<LocalDate> heats = new TreeSet<>();
        heatDates.stream().filter(d -> since == null || !d.isBefore(since)).forEach(heats::add);
        TreeSet<LocalDate> obs = new TreeSet<>(heats);
        lac.forEach(v -> obs.add(v.date()));

        Set<Long> failed = new HashSet<>();
        for (Svc v : lac) {
            if (v.pregnancy() == Pregnancy.NOT_PREGNANT) {
                failed.add(v.id());
            } else if (v.pregnancy() == Pregnancy.UNKNOWN && obs.ceiling(v.date().plusDays(RETURN_DAYS)) != null) {
                failed.add(v.id());
            }
        }
        Svc current = null;
        for (Svc v : lac) {
            if (!failed.contains(v.id())) {
                current = v;
            }
        }

        Status status;
        if (current != null) {
            if (current.pregnancy() == Pregnancy.PREGNANT && current.expectedDue() != null
                    && current.expectedDue().isBefore(today)) {
                status = Status.OVERDUE;
            } else if (current.pregnancy() == Pregnancy.PREGNANT) {
                status = cow.category() == Category.DRY ? Status.DRY_PREGNANT : Status.PREGNANT;
            } else {
                status = Status.SERVED;
            }
        } else if (heifer && lac.isEmpty()) {
            status = Status.HEIFER_NOT_SERVED;
        } else {
            status = Status.OPEN;
        }
        boolean pregnant = status == Status.PREGNANT || status == Status.DRY_PREGNANT || status == Status.OVERDUE;

        Integer daysOpen = since == null ? null
                : (int) ChronoUnit.DAYS.between(since, current != null ? current.date() : today);

        // Heat cycles expected since the voluntary wait (or first-service age) with nothing seen near them.
        LocalDate start = since != null ? since.plusDays(s.voluntaryWaitDays())
                : cow.dateOfBirth() != null ? cow.dateOfBirth().plusMonths(s.heiferFirstServiceMonths()) : null;
        LocalDate end = current != null ? current.date() : today;
        int expected = 0;
        List<LocalDate> missedDates = new ArrayList<>();
        LocalDate next = null;
        if (start != null) {
            LocalDate t = start;
            LocalDate before = obs.lower(start.minusDays(WINDOW));
            if (before != null) {
                t = before.plusDays(cycle);
                while (t.isBefore(start.minusDays(WINDOW))) {
                    t = t.plusDays(cycle);
                }
            }
            while (!t.isAfter(end.minusDays(WINDOW))) {
                LocalDate o = obs.ceiling(t.minusDays(WINDOW));
                if (o != null && !o.isAfter(t.plusDays(WINDOW))) {
                    expected++;
                    t = o.plusDays(cycle);
                } else if (o != null && o.isBefore(t.plusDays(cycle - WINDOW)) && !o.isAfter(end)) {
                    expected += 2;
                    missedDates.add(t);
                    t = o.plusDays(cycle);
                } else {
                    expected++;
                    missedDates.add(t);
                    t = t.plusDays(cycle);
                }
            }
        }
        // Next expected heat: a cycle after the last heat or service seen, else from the wait-period end.
        LocalDate lastObs = obs.floor(today);
        LocalDate base = lastObs != null && (start == null || !lastObs.isBefore(start.minusDays(s.heatCycleDays())))
                ? lastObs.plusDays(cycle) : start;
        if (base != null) {
            while (base.isBefore(today.minusDays(2))) {
                base = base.plusDays(cycle);
            }
            next = base;
        }
        LocalDate nextHeat = switch (status) {
            case OPEN, HEIFER_NOT_SERVED -> next;
            case SERVED -> current.date().plusDays(cycle);
            default -> null;
        };

        LocalDate pdDue = status == Status.SERVED ? current.date().plusDays(s.pdAfterDays()) : null;
        LocalDate due = current != null ? current.expectedDue() : null;
        LocalDate dryOff = pregnant && due != null ? due.minusDays(s.dryOffDaysBeforeDue()) : null;
        Integer daysToDue = pregnant && due != null ? (int) ChronoUnit.DAYS.between(today, due) : null;
        BigDecimal spend = lac.stream().map(Svc::cost).filter(c -> c != null).reduce(BigDecimal.ZERO, BigDecimal::add);

        Suggestion suggestion = suggest(s, cow, status, today, daysOpen, failed.size(), missedDates.size(), nextHeat,
                pdDue, dryOff, daysToDue, current);

        CowStatus cs = new CowStatus(new AnimalRef(cow.id(), cow.tag(), cow.name()), cow.breed(), cow.category(),
                cow.lactationNumber(), status, since, daysOpen, heats.isEmpty() ? null : heats.last(), nextHeat,
                lac.isEmpty() ? null : lac.get(lac.size() - 1).date(), current == null ? null : current.id(),
                pdDue, due, dryOff, daysToDue, lac.size(), failed.size(), missedDates.size(), expected,
                showMoney ? spend : null, suggestion);
        return new Result(cs, missedDates, failed, lac);
    }

    private static Suggestion suggest(BreedingSettings s, Cow cow, Status status, LocalDate today, Integer daysOpen,
                                      int failed, int missed, LocalDate nextHeat, LocalDate pdDue, LocalDate dryOff,
                                      Integer daysToDue, Svc current) {
        List<String> reasons = new ArrayList<>();
        if (status == Status.OVERDUE) {
            reasons.add("Due " + D.format(current.expectedDue()) + " — " + (-daysToDue) + " days overdue");
            return new Suggestion(Action.CALVING_DUE, "Overdue to calve", reasons);
        }
        if ((status == Status.PREGNANT || status == Status.DRY_PREGNANT) && daysToDue != null && daysToDue <= 7) {
            reasons.add("Due " + D.format(current.expectedDue()) + " — in " + daysToDue + " days");
            return new Suggestion(Action.CALVING_DUE, "Calving due soon", reasons);
        }
        if (status == Status.PREGNANT && dryOff != null && !dryOff.isAfter(today.plusDays(3)) && cow.category() != Category.DRY) {
            reasons.add("Dry-off date " + D.format(dryOff));
            return new Suggestion(Action.DRY_OFF_DUE, "Dry off now", reasons);
        }
        if (status == Status.SERVED && pdDue != null && !pdDue.isAfter(today)) {
            reasons.add("Served " + D.format(current.date()) + " — "
                    + ChronoUnit.DAYS.between(current.date(), today) + " days ago");
            return new Suggestion(Action.PD_DUE, "Pregnancy check due", reasons);
        }
        if (status == Status.OPEN || status == Status.HEIFER_NOT_SERVED) {
            String milk = cow.milkPerDay() != null && cow.milkPerDay().signum() > 0
                    ? "Still giving " + cow.milkPerDay().setScale(1, RoundingMode.HALF_UP).toPlainString() + " L/day" : null;
            if (failed > 0) {
                reasons.add(failed + (failed == 1 ? " failed service" : " failed services"));
            }
            if (missed > 0) {
                reasons.add(missed + (missed == 1 ? " heat missed" : " heats missed"));
            }
            boolean overDays = daysOpen != null && daysOpen > s.sellDaysOpen();
            if (overDays || failed >= s.sellFailedServices()) {
                if (overDays) {
                    reasons.add("Not pregnant by day " + s.sellDaysOpen() + " (" + daysOpen + " days open)");
                }
                if (milk != null) {
                    reasons.add(milk);
                }
                return new Suggestion(Action.CONSIDER_SELLING, "Consider selling", reasons);
            }
            if ((missed >= 1 && daysOpen != null && daysOpen > s.voluntaryWaitDays() + s.heatCycleDays()) || failed >= 2) {
                if (milk != null) {
                    reasons.add(milk);
                }
                if (daysOpen != null) {
                    reasons.add("Consider selling if not pregnant by day " + s.sellDaysOpen() + " ("
                            + (s.sellDaysOpen() - daysOpen) + " days left)");
                }
                return new Suggestion(Action.VET_CHECK, "Vet check, then decide", reasons);
            }
            if (status == Status.HEIFER_NOT_SERVED && cow.dateOfBirth() != null
                    && !cow.dateOfBirth().plusMonths(s.heiferFirstServiceMonths()).isAfter(today)) {
                reasons.add(ChronoUnit.MONTHS.between(cow.dateOfBirth(), today) + " months old");
                if (cow.weightKg() != null) {
                    reasons.add("Weight " + cow.weightKg().stripTrailingZeros().toPlainString() + " kg (target "
                            + s.heiferFirstServiceKg().stripTrailingZeros().toPlainString() + " kg)");
                }
                return new Suggestion(Action.FIRST_SERVICE_DUE, "First service due", reasons);
            }
            if (nextHeat != null && Math.abs(ChronoUnit.DAYS.between(today, nextHeat)) <= 2) {
                reasons.add("Next heat expected " + D.format(nextHeat));
                return new Suggestion(Action.HEAT_DUE, "Heat due", reasons);
            }
            if (missed > 0 || failed > 0) {
                return new Suggestion(Action.OK, "Keep watching", reasons);
            }
        }
        return new Suggestion(Action.OK, "On track", reasons);
    }
}
