package com.dairyfarm.breeding;

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

import com.dairyfarm.breeding.BreedingViews.CalvingRequest;
import com.dairyfarm.breeding.BreedingViews.CalvingResult;
import com.dairyfarm.breeding.BreedingViews.ColostrumRequest;
import com.dairyfarm.breeding.BreedingViews.Heat;
import com.dairyfarm.breeding.BreedingViews.HeatRequest;
import com.dairyfarm.breeding.BreedingViews.HeatSaved;
import com.dairyfarm.breeding.BreedingViews.HeatWatchItem;
import com.dairyfarm.breeding.BreedingViews.History;
import com.dairyfarm.breeding.BreedingViews.Overview;
import com.dairyfarm.breeding.BreedingViews.PregCheckRequest;
import com.dairyfarm.breeding.BreedingViews.ServiceRequest;
import com.dairyfarm.breeding.BreedingViews.ServiceView;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/breeding")
@Tag(name = "Breeding")
public class BreedingController {

    private final BreedingService breeding;
    private final BreedingQueryService query;
    private final FarmContext ctx;

    public BreedingController(BreedingService breeding, BreedingQueryService query, FarmContext ctx) {
        this.breeding = breeding;
        this.query = query;
        this.ctx = ctx;
    }

    @GetMapping("/settings")
    @Operation(summary = "Gestation, heat cycle, dry-off, keep-or-sell and calf rearing rules")
    public BreedingSettings settings(@PathVariable Long farmId) {
        return breeding.settings(ctx.farmId());
    }

    @PutMapping("/settings")
    @OwnerOnly
    public BreedingSettings updateSettings(@PathVariable Long farmId, @Valid @RequestBody BreedingSettings req) {
        return breeding.updateSettings(ctx.farmId(), req);
    }

    @GetMapping("/overview")
    @Operation(summary = "Breedable count, next 14 days and every female grouped by breeding status")
    public Overview overview(@PathVariable Long farmId) {
        return query.overview(ctx.farmId(), ctx.isOwner());
    }

    @GetMapping("/heat-watch")
    public List<HeatWatchItem> heatWatch(@PathVariable Long farmId) {
        return query.heatWatch(ctx.farmId(), ctx.isOwner());
    }

    @GetMapping("/animals/{animalId}/history")
    @Operation(summary = "One female's breeding status, this lactation's events and earlier lactations")
    public History history(@PathVariable Long farmId, @PathVariable Long animalId) {
        return query.history(ctx.farmId(), animalId, ctx.isOwner());
    }

    @PostMapping("/heats")
    @ResponseStatus(HttpStatus.CREATED)
    public HeatSaved createHeat(@PathVariable Long farmId, @Valid @RequestBody HeatRequest req) {
        return breeding.createHeat(ctx.farmId(), req);
    }

    @GetMapping("/heats")
    public List<Heat> heats(@PathVariable Long farmId, @RequestParam(required = false) Long animalId,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return breeding.heats(ctx.farmId(), animalId, from, to);
    }

    @DeleteMapping("/heats/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHeat(@PathVariable Long farmId, @PathVariable Long id) {
        breeding.deleteHeat(ctx.farmId(), id);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a service (AI/ET uses straws from the semen batch)")
    public ServiceView createService(@PathVariable Long farmId, @Valid @RequestBody ServiceRequest req) {
        return breeding.createService(ctx.farmId(), req, ctx.isOwner());
    }

    @GetMapping("/services")
    public List<ServiceView> services(@PathVariable Long farmId, @RequestParam(required = false) Long animalId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return breeding.services(ctx.farmId(), animalId, from, to, ctx.isOwner());
    }

    @GetMapping("/services/{id}")
    public ServiceView service(@PathVariable Long farmId, @PathVariable Long id) {
        return breeding.service(ctx.farmId(), id, ctx.isOwner());
    }

    @PutMapping("/services/{id}")
    public ServiceView updateService(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody ServiceRequest req) {
        return breeding.updateService(ctx.farmId(), id, req, ctx.isOwner());
    }

    @DeleteMapping("/services/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a service without a calving; its straws go back to stock")
    public void deleteService(@PathVariable Long farmId, @PathVariable Long id) {
        breeding.deleteService(ctx.farmId(), id);
    }

    @PostMapping("/services/{id}/pregnancy-check")
    public ServiceView pregnancyCheck(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody PregCheckRequest req) {
        return breeding.pregnancyCheck(ctx.farmId(), id, req, ctx.isOwner());
    }

    @PutMapping("/services/{id}/colostrum")
    public ServiceView colostrum(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody ColostrumRequest req) {
        return breeding.colostrum(ctx.farmId(), id, req, ctx.isOwner());
    }

    @PostMapping("/calvings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a calving: creates the calves and starts the dam's new lactation")
    public CalvingResult calving(@PathVariable Long farmId, @Valid @RequestBody CalvingRequest req) {
        return breeding.calving(ctx.farmId(), req, ctx.isOwner());
    }
}
