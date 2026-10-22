package com.dairyfarm.breeding;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.breeding.BreedingViews.AdjustRequest;
import com.dairyfarm.breeding.BreedingViews.SemenBatch;
import com.dairyfarm.breeding.BreedingViews.SemenList;
import com.dairyfarm.breeding.BreedingViews.SemenRequest;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/semen")
@Tag(name = "Semen stock")
public class SemenController {

    private final SemenService semen;
    private final FarmContext ctx;

    public SemenController(SemenService semen, FarmContext ctx) {
        this.semen = semen;
        this.ctx = ctx;
    }

    @GetMapping
    public SemenList list(@PathVariable Long farmId) {
        return semen.list(ctx.farmId(), ctx.isOwner());
    }

    @GetMapping("/{id}")
    public SemenBatch get(@PathVariable Long farmId, @PathVariable Long id) {
        return semen.get(ctx.farmId(), id, ctx.isOwner());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SemenBatch create(@PathVariable Long farmId, @Valid @RequestBody SemenRequest req) {
        return semen.create(ctx.farmId(), req, ctx.isOwner());
    }

    @PutMapping("/{id}")
    public SemenBatch update(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody SemenRequest req) {
        return semen.update(ctx.farmId(), id, req, ctx.isOwner());
    }

    @PostMapping("/{id}/adjust")
    public SemenBatch adjust(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody AdjustRequest req) {
        return semen.adjust(ctx.farmId(), id, req, ctx.isOwner());
    }

    @DeleteMapping("/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long farmId, @PathVariable Long id) {
        semen.delete(ctx.farmId(), id);
    }
}
