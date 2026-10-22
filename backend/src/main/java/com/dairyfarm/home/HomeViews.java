package com.dairyfarm.home;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.dairyfarm.alert.AlertViews.Alert;
import com.dairyfarm.alert.AlertViews.MemberRef;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Shapes for the home screen, the daily checklist and barn readings. */
public final class HomeViews {

    private HomeViews() {
    }

    public enum TaskStatus { DONE, DUE, LATE, PENDING }

    public enum BarnLevel { NONE, MILD, MODERATE, SEVERE, EMERGENCY }

    public record AnimalRef(Long id, String tag, String name) {
    }

    public record Link(String kind, Long id) {
    }

    public record Tiles(BigDecimal milkToday, BigDecimal milkChangePct, String milkCompareNote, int milkingCows, int dryCows,
                        int herd, int alertsOpen, int alertsOverdue, BigDecimal marginMonthPct, Integer marginAlertPct) {
    }

    public record HomeTask(String key, String title, TaskStatus status, String detail,
                           @JsonFormat(pattern = "HH:mm") LocalTime time, Link link) {
    }

    public record Progress(int done, int total) {
    }

    public record Producer(AnimalRef animal, BigDecimal avgLitres7d) {
    }

    public record Calving(AnimalRef animal, LocalDate dueDate, long days, String note) {
    }

    public record FeedingReminder(String title, String detail, TaskStatus status) {
    }

    public record Home(String role, String greeting, LocalDate date, Tiles tiles, List<HomeTask> tasks, Progress checklist,
                       Barn barn, List<Producer> topProducers, List<Calving> upcomingCalvings, List<Alert> healthReminders,
                       List<FeedingReminder> feedingReminders) {
    }

    // ---------- checklist ----------

    public record Task(String key, Long id, String name, boolean builtIn, boolean done, MemberRef doneBy, Instant doneAt,
                       String detail, boolean auto, boolean workerVisible, Boolean repeatDaily, Link link) {
    }

    public record Checklist(LocalDate date, int done, int total, List<Task> builtIn, List<Task> custom) {
    }

    public record TickRequest(@NotNull(message = "Say done or not") Boolean done) {
    }

    public record TaskRequest(@NotBlank(message = "Enter the task") @Size(max = 120) String name, Boolean repeatDaily,
                              Boolean workerVisible, LocalDate date) {
    }

    // ---------- barn ----------

    public record Barn(LocalDate date, @JsonFormat(pattern = "HH:mm") LocalTime time, BigDecimal temperatureC,
                      BigDecimal humidityPct, BigDecimal thi, BarnLevel level, String advice, String yieldEffect) {
    }

    public record BarnRequest(
            @NotNull(message = "Pick the date") LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            @NotNull(message = "Enter the temperature") @DecimalMin(value = "-10", message = "Use −10 to 55 °C")
            @DecimalMax(value = "55", message = "Use −10 to 55 °C") BigDecimal temperatureC,
            @NotNull(message = "Enter the humidity") @DecimalMin(value = "0", message = "Use 0 to 100 %")
            @DecimalMax(value = "100", message = "Use 0 to 100 %") BigDecimal humidityPct) {
    }
}
