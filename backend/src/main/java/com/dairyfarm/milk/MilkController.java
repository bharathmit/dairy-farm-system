package com.dairyfarm.milk;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.milk.MilkViews.CowDay;
import com.dairyfarm.milk.MilkViews.CowDayForm;
import com.dairyfarm.milk.MilkViews.CowDayRequest;
import com.dairyfarm.milk.MilkViews.DaySummary;
import com.dairyfarm.milk.MilkViews.Sale;
import com.dairyfarm.milk.MilkViews.SaleRequest;
import com.dairyfarm.milk.MilkViews.Session;
import com.dairyfarm.milk.MilkViews.Sheet;
import com.dairyfarm.milk.MilkViews.SheetRequest;
import com.dairyfarm.milk.MilkViews.TrendPoint;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/milk")
@Tag(name = "Milk")
public class MilkController {

    private final MilkService milk;
    private final FarmContext ctx;

    public MilkController(MilkService milk, FarmContext ctx) {
        this.milk = milk;
        this.ctx = ctx;
    }

    @GetMapping("/grading")
    @Operation(summary = "Milk grade rules (expected yield curve, grade bands, quality checks)")
    public MilkGradingRules grading(@PathVariable Long farmId) {
        return milk.grading(ctx.farmId());
    }

    @PutMapping("/grading")
    @OwnerOnly
    public MilkGradingRules updateGrading(@PathVariable Long farmId, @Valid @RequestBody MilkGradingRules rules) {
        return milk.updateGrading(ctx.farmId(), rules);
    }

    @GetMapping("/day")
    @Operation(summary = "One day: AM/PM totals, yesterday comparison, each cow's litres and grade")
    public DaySummary day(@PathVariable Long farmId,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return milk.day(ctx.farmId(), date == null ? DailyDates.today() : date);
    }

    @GetMapping("/trend")
    @Operation(summary = "Herd litres per day (cow records), zero-filled")
    public List<TrendPoint> trend(@PathVariable Long farmId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return milk.trend(ctx.farmId(), from, to);
    }

    @GetMapping("/grades")
    @Operation(summary = "How many graded cow-days got A, B, C, D")
    public Map<String, Long> grades(@PathVariable Long farmId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return milk.grades(ctx.farmId(), from, to);
    }

    @GetMapping("/sheet")
    @Operation(summary = "Herd milking sheet for one session")
    public Sheet sheet(@PathVariable Long farmId,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam Session session, @RequestParam(required = false) Long groupId) {
        return milk.sheet(ctx.farmId(), date, session, groupId);
    }

    @PutMapping("/sheet")
    @Operation(summary = "Save a session for many cows; litres null = skipped")
    public Sheet saveSheet(@PathVariable Long farmId, @Valid @RequestBody SheetRequest req) {
        return milk.saveSheet(ctx.farmId(), req);
    }

    @GetMapping("/cows/{animalId}/days/{date}")
    public CowDayForm cowDay(@PathVariable Long farmId, @PathVariable Long animalId,
                             @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return milk.cowDay(ctx.farmId(), animalId, date);
    }

    @PutMapping("/cows/{animalId}/days/{date}")
    @Operation(summary = "Save one cow's day (AM, PM, quality); null AM/PM removes that session")
    public CowDayForm saveCowDay(@PathVariable Long farmId, @PathVariable Long animalId,
                                 @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @Valid @RequestBody CowDayRequest req) {
        return milk.saveCowDay(ctx.farmId(), animalId, date, req);
    }

    @GetMapping("/cows/{animalId}")
    public List<CowDay> cowHistory(@PathVariable Long farmId, @PathVariable Long animalId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return milk.cowHistory(ctx.farmId(), animalId, from, to);
    }

    @GetMapping("/sales")
    public List<Sale> sales(@PathVariable Long farmId,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return milk.sales(ctx.farmId(), from, to, ctx.isOwner());
    }

    @PutMapping("/sales")
    @Operation(summary = "Farm total / dairy sale for a date and session (saved again = updated)")
    public Sale saveSale(@PathVariable Long farmId, @Valid @RequestBody SaleRequest req) {
        return milk.saveSale(ctx.farmId(), req, ctx.isOwner());
    }

    @DeleteMapping("/sales/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSale(@PathVariable Long farmId, @PathVariable Long id) {
        milk.deleteSale(ctx.farmId(), id);
    }
}
