package com.dairyfarm.alert;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.alert.AlertViews.Alert;
import com.dairyfarm.alert.AlertViews.AlertCategory;
import com.dairyfarm.alert.AlertViews.AlertList;
import com.dairyfarm.alert.AlertViews.AlertStatus;
import com.dairyfarm.alert.AlertViews.RefreshResult;
import com.dairyfarm.alert.AlertViews.ReminderRequest;
import com.dairyfarm.alert.AlertViews.Rule;
import com.dairyfarm.alert.AlertViews.RuleUpdate;
import com.dairyfarm.alert.AlertViews.Schedule;
import com.dairyfarm.alert.AlertViews.ScheduleRequest;
import com.dairyfarm.alert.AlertViews.Severity;
import com.dairyfarm.alert.AlertViews.SnoozeRequest;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/farms/{farmId}")
@Tag(name = "Alerts")
public class AlertController {

    private final AlertService alerts;
    private final HealthScheduleService schedules;
    private final FarmContext ctx;

    public AlertController(AlertService alerts, HealthScheduleService schedules, FarmContext ctx) {
        this.alerts = alerts;
        this.schedules = schedules;
        this.ctx = ctx;
    }

    @GetMapping("/alerts")
    @Operation(summary = "Alerts grouped by overdue / today / this week / later (workers see only worker-visible ones)")
    public AlertList list(@PathVariable Long farmId, @RequestParam(required = false) AlertCategory category,
                          @RequestParam(required = false) Severity severity,
                          @RequestParam(required = false) AlertStatus status,
                          @RequestParam(required = false) Long animalId) {
        return alerts.list(ctx.farmId(), ctx.isOwner(), category, severity, status, animalId);
    }

    @GetMapping("/alerts/rules")
    @OwnerOnly
    public List<Rule> rules(@PathVariable Long farmId) {
        return alerts.rules(ctx.farmId());
    }

    @PutMapping("/alerts/rules")
    @OwnerOnly
    @Operation(summary = "Change alert rules; shared numbers are written to their own settings")
    public List<Rule> saveRules(@PathVariable Long farmId, @Valid @RequestBody @Size(max = 50) List<@Valid RuleUpdate> body) {
        return alerts.saveRules(ctx.farmId(), body);
    }

    @PostMapping("/alerts/refresh")
    @Operation(summary = "Run the alert rules now")
    public RefreshResult refresh(@PathVariable Long farmId) {
        return alerts.refresh(ctx.farmId());
    }

    @GetMapping("/alerts/{id}")
    public Alert get(@PathVariable Long farmId, @PathVariable Long id) {
        return alerts.get(ctx.farmId(), id, ctx.isOwner());
    }

    @PostMapping("/alerts/{id}/done")
    public Alert done(@PathVariable Long farmId, @PathVariable Long id) {
        return alerts.done(ctx.farmId(), id, ctx.isOwner());
    }

    @PostMapping("/alerts/{id}/dismiss")
    public Alert dismiss(@PathVariable Long farmId, @PathVariable Long id) {
        return alerts.dismiss(ctx.farmId(), id, ctx.isOwner());
    }

    @PostMapping("/alerts/{id}/snooze")
    public Alert snooze(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody SnoozeRequest req) {
        return alerts.snooze(ctx.farmId(), id, req.until(), ctx.isOwner());
    }

    @PostMapping("/reminders")
    @ResponseStatus(HttpStatus.CREATED)
    public Alert createReminder(@PathVariable Long farmId, @Valid @RequestBody ReminderRequest req) {
        return alerts.createReminder(ctx.farmId(), req, ctx.isOwner());
    }

    @PutMapping("/reminders/{id}")
    public Alert updateReminder(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody ReminderRequest req) {
        return alerts.updateReminder(ctx.farmId(), id, req, ctx.isOwner());
    }

    @DeleteMapping("/reminders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReminder(@PathVariable Long farmId, @PathVariable Long id) {
        alerts.deleteReminder(ctx.farmId(), id, ctx.isOwner());
    }

    // ---------- health schedules ----------

    @GetMapping("/health-schedules")
    public List<Schedule> schedules(@PathVariable Long farmId) {
        return schedules.list(ctx.farmId());
    }

    @GetMapping("/health-schedules/{id}")
    public Schedule schedule(@PathVariable Long farmId, @PathVariable Long id) {
        return schedules.get(ctx.farmId(), id);
    }

    @PostMapping("/health-schedules")
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    public Schedule createSchedule(@PathVariable Long farmId, @Valid @RequestBody ScheduleRequest req) {
        return schedules.create(ctx.farmId(), req);
    }

    @PutMapping("/health-schedules/{id}")
    @OwnerOnly
    public Schedule updateSchedule(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody ScheduleRequest req) {
        return schedules.update(ctx.farmId(), id, req);
    }

    @DeleteMapping("/health-schedules/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchedule(@PathVariable Long farmId, @PathVariable Long id) {
        schedules.delete(ctx.farmId(), id);
    }
}
