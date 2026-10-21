package com.dairyfarm.record;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.dairyfarm.common.PageResponse;
import com.dairyfarm.record.HealthViews.BatchResult;
import com.dairyfarm.record.HealthViews.FollowUpRequest;
import com.dairyfarm.record.HealthViews.HealthBatchRequest;
import com.dairyfarm.record.HealthViews.HealthRecord;
import com.dairyfarm.record.HealthViews.HealthRequest;
import com.dairyfarm.record.HealthViews.HealthType;
import com.dairyfarm.record.HealthViews.Overview;
import com.dairyfarm.record.HealthViews.Withdrawals;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/health")
@Tag(name = "Health")
public class HealthController {

    private final HealthService health;
    private final FarmContext ctx;

    public HealthController(HealthService health, FarmContext ctx) {
        this.health = health;
        this.ctx = ctx;
    }

    @GetMapping("/overview")
    @Operation(summary = "Counts, follow-ups due in the next 7 days (and overdue), and the latest records")
    public Overview overview(@PathVariable Long farmId) {
        return health.overview(ctx.farmId(), ctx.isOwner());
    }

    @GetMapping("/withdrawals")
    @Operation(summary = "Cows whose milk must not be sold now, and withdrawals ended in the last 14 days")
    public Withdrawals withdrawals(@PathVariable Long farmId) {
        return health.withdrawals(ctx.farmId(), ctx.isOwner());
    }

    @GetMapping("/records")
    public PageResponse<HealthRecord> list(@PathVariable Long farmId,
                                           @RequestParam(required = false) Long animalId,
                                           @RequestParam(required = false) HealthType type,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return health.list(ctx.farmId(), animalId, type, from, to, Math.max(page, 0), size, ctx.isOwner());
    }

    @GetMapping("/records/{id}")
    public HealthRecord get(@PathVariable Long farmId, @PathVariable Long id) {
        return health.get(ctx.farmId(), id, ctx.isOwner());
    }

    @PostMapping("/records")
    @ResponseStatus(HttpStatus.CREATED)
    public HealthRecord create(@PathVariable Long farmId, @Valid @RequestBody HealthRequest req) {
        return health.create(ctx.farmId(), req, ctx.isOwner());
    }

    @PostMapping("/records/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "The same treatment for many animals (one record each, sharing a batchId)")
    public BatchResult batch(@PathVariable Long farmId, @Valid @RequestBody HealthBatchRequest req) {
        return health.createBatch(ctx.farmId(), req, ctx.isOwner());
    }

    @PutMapping("/records/{id}")
    public HealthRecord update(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody HealthRequest req) {
        return health.update(ctx.farmId(), id, req, ctx.isOwner());
    }

    @PutMapping("/records/{id}/follow-up")
    public HealthRecord followUp(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody FollowUpRequest req) {
        return health.followUp(ctx.farmId(), id, req.done(), ctx.isOwner());
    }

    @DeleteMapping("/records/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long farmId, @PathVariable Long id) {
        health.delete(ctx.farmId(), id);
    }
}
