package com.dairyfarm.farm;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.security.CurrentUser;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/farms")
@Tag(name = "Farms & settings")
public class FarmController {

    private final FarmService farms;
    private final FarmQueryService queries;
    private final FarmContext ctx;

    public FarmController(FarmService farms, FarmQueryService queries, FarmContext ctx) {
        this.farms = farms;
        this.queries = queries;
        this.ctx = ctx;
    }

    @GetMapping
    @Operation(summary = "Farms I belong to, with my role and herd size")
    public List<FarmSummary> myFarms() {
        return queries.farmsOf(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a farm; I become its owner")
    public FarmResponse create(@Valid @RequestBody FarmRequest req) {
        return farms.create(req);
    }

    @GetMapping("/{farmId}")
    @Operation(summary = "Farm details")
    public FarmResponse get(@PathVariable Long farmId) {
        return farms.get(ctx.farmId(), ctx.role());
    }

    @PutMapping("/{farmId}")
    @OwnerOnly
    @Operation(summary = "Update farm name and location (owner)")
    public FarmResponse update(@PathVariable Long farmId, @Valid @RequestBody FarmRequest req) {
        return farms.update(ctx.farmId(), req);
    }

    @GetMapping("/{farmId}/settings")
    @Operation(summary = "Milk rate, milking times, margin alert and reminder settings")
    public FarmSettings settings(@PathVariable Long farmId) {
        return farms.settings(ctx.farmId());
    }

    @PutMapping("/{farmId}/settings")
    @OwnerOnly
    @Operation(summary = "Change settings (owner). Send only the values to change.")
    public FarmSettings updateSettings(@PathVariable Long farmId, @Valid @RequestBody FarmSettings patch) {
        return farms.updateSettings(ctx.farmId(), patch);
    }
}
