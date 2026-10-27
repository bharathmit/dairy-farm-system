package com.dairyfarm.finance;

import java.time.LocalDate;
import java.util.List;

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
import com.dairyfarm.finance.FinanceViews.*;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Finance is owner only. */
@RestController
@RequestMapping("/api/v1/farms/{farmId}/finance")
@Tag(name = "Finance")
@OwnerOnly
public class FinanceController {

    private final FinanceService finance;
    private final FarmContext ctx;

    public FinanceController(FinanceService finance, FarmContext ctx) {
        this.finance = finance;
        this.ctx = ctx;
    }

    @GetMapping("/summary")
    @Operation(summary = "Income, expense, net, margin and categories for a period (default: this month)")
    public Summary summary(@PathVariable Long farmId, @RequestParam(required = false) LocalDate from,
                           @RequestParam(required = false) LocalDate to) {
        return finance.summary(ctx.farmId(), from, to);
    }

    @GetMapping("/daily")
    @Operation(summary = "Per day: feed fed cost, milk income, other income, other expenses (max 1 year)")
    public List<DailyMoney> daily(@PathVariable Long farmId,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return finance.daily(ctx.farmId(), from, to);
    }

    @GetMapping("/entries")
    public PageResponse<Entry> entries(@PathVariable Long farmId, @RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to,
                                       @RequestParam(required = false) Direction direction,
                                       @RequestParam(required = false) Category category,
                                       @RequestParam(required = false) Status status,
                                       @RequestParam(required = false) Long animalId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return finance.entries(ctx.farmId(), from, to, direction, category, status, animalId, page, size);
    }

    @GetMapping("/entries/{id}")
    public Entry get(@PathVariable Long farmId, @PathVariable Long id) {
        return finance.get(ctx.farmId(), id);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public Entry create(@PathVariable Long farmId, @Valid @RequestBody EntryRequest req) {
        return finance.create(ctx.farmId(), req);
    }

    @PutMapping("/entries/{id}")
    @Operation(summary = "Edit an entry; automatic entries only change status, payment method and notes")
    public Entry update(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody EntryRequest req) {
        return finance.update(ctx.farmId(), id, req);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long farmId, @PathVariable Long id) {
        finance.delete(ctx.farmId(), id);
    }

    @GetMapping("/pnl")
    @Operation(summary = "Profit & loss for the farm and per animal (no dates = lifetime)")
    public Pnl pnl(@PathVariable Long farmId, @RequestParam(defaultValue = "ANIMAL") PnlScope scope,
                   @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return finance.pnl(ctx.farmId(), scope, from, to);
    }

    @GetMapping("/pnl/animals/{animalId}")
    public AnimalPnl animalPnl(@PathVariable Long farmId, @PathVariable Long animalId,
                               @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return finance.animalPnl(ctx.farmId(), animalId, from, to);
    }
}
