package com.dairyfarm.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes for alerts, reminders, alert rules and health schedules. */
public final class AlertViews {

    private AlertViews() {
    }

    public enum Source { RULE, REMINDER, PROCUREMENT }

    public enum AlertCategory { REPRODUCTION, CALF, HEALTH, MILK, FEED, FINANCE_OPS }

    public enum Severity { HIGH, MEDIUM, INFO }

    public enum AlertStatus { OPEN, DONE, DISMISSED }

    public enum Repeat { NONE, DAILY, WEEKLY, MONTHLY }

    public enum ReminderType { HEALTH, BREED, FEED, VET, OTHER }

    public enum ActionKind { LOG_HEAT, LOG_SERVICE, LOG_PD, LOG_CALVING, LOG_WEIGHT, LOG_HEALTH, OPEN_FEED, OPEN_PROCUREMENT,
        OPEN_EQUIPMENT, NONE }

    public record Fact(String label, String value) {
    }

    public record Action(ActionKind kind, String label, Map<String, Object> params) {
        public static final Action NONE = new Action(ActionKind.NONE, null, Map.of());
    }

    public record AlertAnimal(Long id, String tag, String name, String category, String breed) {
    }

    public record MemberRef(Long id, String name) {
    }

    public record Alert(Long id, Source source, String ruleCode, AlertCategory category, Severity severity, String title,
                        String detail, AlertAnimal animal, LocalDate dueDate,
                        @JsonFormat(pattern = "HH:mm") LocalTime dueTime, Integer overdueDays, AlertStatus status,
                        Instant snoozedUntil, boolean workerVisible, Repeat repeat, ReminderType type, String why,
                        List<Fact> facts, Action action, MemberRef doneBy, Instant doneAt, boolean autoClosed,
                        String notes) {
    }

    public record Counts(int all, Map<AlertCategory, Integer> byCategory, Map<Severity, Integer> bySeverity, int overdue) {
    }

    public record AlertList(Counts counts, List<Alert> overdue, List<Alert> today, List<Alert> thisWeek, List<Alert> later) {
    }

    public record RefreshResult(int created, int closed) {
    }

    public record SnoozeRequest(@NotNull(message = "Pick until when") Instant until) {
    }

    public record ReminderRequest(
            @NotBlank(message = "Enter a title") @Size(max = 160, message = "Keep the title under 160 characters") String title,
            @Size(max = 2000) String notes,
            @NotNull(message = "Pick the date") LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            ReminderType type,
            Long animalId,
            Boolean workerVisible,
            Repeat repeat) {
    }

    // ---------- rules ----------

    public record Rule(String code, AlertCategory category, String title, String description, boolean enabled,
                       boolean workerVisible, Map<String, BigDecimal> params, boolean alwaysOn, boolean ownerOnly) {
    }

    public record RuleUpdate(@NotBlank String code, Boolean enabled, Boolean workerVisible, Map<String, BigDecimal> params) {
    }

    // ---------- health schedules ----------

    public enum ScheduleType { DEWORMING, VACCINATION }

    public enum AppliesTo { CALVES_HEIFERS, ALL, MILKING }

    public record Step(
            @NotBlank(message = "Name the dose") @Size(max = 80) String label,
            @NotNull(message = "Enter the age") @Min(value = 0, message = "Use 0 to 7300 days") @Max(value = 7300, message = "Use 0 to 7300 days") Integer atAgeDays,
            @Min(value = 1, message = "Use 1 to 3650 days") @Max(value = 3650, message = "Use 1 to 3650 days") Integer repeatEveryDays,
            @Min(value = 0, message = "Use 0 to 7300 days") @Max(value = 7300, message = "Use 0 to 7300 days") Integer untilAgeDays) {
    }

    public record Schedule(Long id, ScheduleType type, String name, AppliesTo appliesTo, String medicine, List<Step> steps,
                           int alertDaysBefore, boolean workerVisible, boolean active) {
    }

    public record ScheduleRequest(
            @NotNull(message = "Pick the type") ScheduleType type,
            @NotBlank(message = "Enter a name") @Size(max = 120) String name,
            @NotNull(message = "Pick who it applies to") AppliesTo appliesTo,
            @Size(max = 120) String medicine,
            @NotEmpty(message = "Add at least one dose") @Size(max = 20) List<@Valid Step> steps,
            @Min(value = 0, message = "Use 0 to 60 days") @Max(value = 60, message = "Use 0 to 60 days") Integer alertDaysBefore,
            Boolean workerVisible,
            Boolean active) {
    }
}
