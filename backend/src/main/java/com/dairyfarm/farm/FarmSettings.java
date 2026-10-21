package com.dairyfarm.farm;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Farm-wide settings (stored as JSONB on farm): milk price, milking times, margin alert and
 * reminder options. Missing values fall back to the defaults.
 */
public record FarmSettings(
        @DecimalMin(value = "0.0", message = "Milk rate can't be negative")
        @DecimalMax(value = "1000.0", message = "Milk rate looks too high")
        BigDecimal milkRate,
        LocalTime morningMilking,
        LocalTime eveningMilking,
        @Min(value = 0, message = "Use a value from 0 to 100") @Max(value = 100, message = "Use a value from 0 to 100")
        Integer marginAlertPct,
        @Valid Notifications notifications) {

    public record Notifications(
            Boolean enabled,
            Boolean health,
            Boolean breeding,
            Boolean checklist,
            @Min(value = 0, message = "Use 0, 1, 3 or 7") @Max(value = 7, message = "Use 0, 1, 3 or 7")
            Integer leadDays,
            LocalTime reminderTime,
            Boolean digest,
            LocalTime digestTime,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd) {

        public static final Notifications DEFAULTS = new Notifications(true, true, true, true, 1,
                LocalTime.of(8, 0), true, LocalTime.of(7, 0), LocalTime.of(22, 0), LocalTime.of(5, 0));

        public Notifications resolved() {
            var d = DEFAULTS;
            return new Notifications(
                    or(enabled, d.enabled), or(health, d.health), or(breeding, d.breeding),
                    or(checklist, d.checklist), or(leadDays, d.leadDays), or(reminderTime, d.reminderTime),
                    or(digest, d.digest), or(digestTime, d.digestTime),
                    or(quietHoursStart, d.quietHoursStart), or(quietHoursEnd, d.quietHoursEnd));
        }
    }

    public static final FarmSettings DEFAULTS = new FarmSettings(null, LocalTime.of(6, 0), LocalTime.of(18, 0), 30,
            Notifications.DEFAULTS);

    public FarmSettings resolved() {
        return new FarmSettings(
                milkRate,
                or(morningMilking, DEFAULTS.morningMilking),
                or(eveningMilking, DEFAULTS.eveningMilking),
                or(marginAlertPct, DEFAULTS.marginAlertPct),
                notifications == null ? Notifications.DEFAULTS : notifications.resolved());
    }

    /** Applies the non-null values of {@code patch} on top of these settings. */
    public FarmSettings merge(FarmSettings patch) {
        var base = resolved();
        if (patch == null) {
            return base;
        }
        var n = base.notifications;
        var p = patch.notifications;
        var notifications = p == null ? n : new Notifications(
                or(p.enabled, n.enabled), or(p.health, n.health), or(p.breeding, n.breeding),
                or(p.checklist, n.checklist), or(p.leadDays, n.leadDays), or(p.reminderTime, n.reminderTime),
                or(p.digest, n.digest), or(p.digestTime, n.digestTime),
                or(p.quietHoursStart, n.quietHoursStart), or(p.quietHoursEnd, n.quietHoursEnd));
        return new FarmSettings(
                or(patch.milkRate, base.milkRate),
                or(patch.morningMilking, base.morningMilking),
                or(patch.eveningMilking, base.eveningMilking),
                or(patch.marginAlertPct, base.marginAlertPct),
                notifications);
    }

    private static <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
