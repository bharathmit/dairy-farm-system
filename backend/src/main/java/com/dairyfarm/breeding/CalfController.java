package com.dairyfarm.breeding;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.breeding.BreedingViews.CalfList;
import com.dairyfarm.breeding.BreedingViews.FeedingDay;
import com.dairyfarm.breeding.BreedingViews.FeedingRequest;
import com.dairyfarm.breeding.BreedingViews.FeedingSheet;
import com.dairyfarm.breeding.BreedingViews.FeedingSheetRequest;
import com.dairyfarm.breeding.BreedingViews.Session;
import com.dairyfarm.common.Sex;
import com.dairyfarm.security.FarmContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/calves")
@Tag(name = "Calves")
public class CalfController {

    private final CalfService calves;
    private final FarmContext ctx;

    public CalfController(CalfService calves, FarmContext ctx) {
        this.calves = calves;
        this.ctx = ctx;
    }

    @GetMapping
    @Operation(summary = "Active calves (counts are unfiltered totals; 'shown' is after the sex/age filter)")
    public CalfList list(@PathVariable Long farmId, @RequestParam(required = false) Sex sex,
                         @RequestParam(required = false) Integer minDays, @RequestParam(required = false) Integer maxDays) {
        return calves.list(ctx.farmId(), sex, minDays, maxDays);
    }

    @GetMapping("/feeding-sheet")
    public FeedingSheet sheet(@PathVariable Long farmId,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestParam(defaultValue = "AM") Session session, @RequestParam(required = false) Sex sex,
                              @RequestParam(required = false) Integer minDays, @RequestParam(required = false) Integer maxDays,
                              @RequestParam(required = false) Long groupId) {
        return calves.sheet(ctx.farmId(), date, session, sex, minDays, maxDays, groupId);
    }

    @PutMapping("/feeding-sheet")
    @Operation(summary = "Save one session's milk (and starter) for a group of calves")
    public FeedingSheet saveSheet(@PathVariable Long farmId, @Valid @RequestBody FeedingSheetRequest req) {
        return calves.saveSheet(ctx.farmId(), req);
    }

    @GetMapping("/{animalId}/feeding")
    public FeedingDay feeding(@PathVariable Long farmId, @PathVariable Long animalId,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return calves.feeding(ctx.farmId(), animalId, date);
    }

    @PutMapping("/{animalId}/feeding/{date}")
    @Operation(summary = "Save one calf's feeding for a day (all fields empty removes it)")
    public FeedingDay saveFeeding(@PathVariable Long farmId, @PathVariable Long animalId,
                                  @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @Valid @RequestBody FeedingRequest req) {
        return calves.saveFeeding(ctx.farmId(), animalId, date, req);
    }
}
