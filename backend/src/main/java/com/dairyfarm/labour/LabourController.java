package com.dairyfarm.labour;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.finance.FinanceViews.Entry;
import com.dairyfarm.labour.LabourViews.*;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Workers, attendance and wages — owner only. */
@RestController
@RequestMapping("/api/v1/farms/{farmId}/labour")
@Tag(name = "Labour")
@OwnerOnly
public class LabourController {

    private final LabourService labour;
    private final FarmContext ctx;

    public LabourController(LabourService labour, FarmContext ctx) {
        this.labour = labour;
        this.ctx = ctx;
    }

    @GetMapping("/workers")
    @Operation(summary = "Paid workers with this week's attendance, wage due and last payment")
    public Workers workers(@PathVariable Long farmId, @RequestParam(required = false) LocalDate weekOf) {
        return labour.workers(ctx.farmId(), weekOf);
    }

    @GetMapping("/attendance")
    public AttendanceWeek attendance(@PathVariable Long farmId, @RequestParam(required = false) LocalDate weekOf) {
        return labour.attendance(ctx.farmId(), weekOf);
    }

    @PutMapping("/attendance")
    @Operation(summary = "Mark days present / half / absent (status null clears a day)")
    public AttendanceWeek saveAttendance(@PathVariable Long farmId, @Valid @RequestBody AttendanceRequest req) {
        return labour.saveAttendance(ctx.farmId(), req);
    }

    @GetMapping("/workers/{memberId}/due")
    public WageDue due(@PathVariable Long farmId, @PathVariable Long memberId,
                       @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return labour.due(ctx.farmId(), memberId, from, to);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a wage payment or an advance (a Labour expense)")
    public Entry pay(@PathVariable Long farmId, @Valid @RequestBody PaymentRequest req) {
        return labour.pay(ctx.farmId(), req);
    }
}
