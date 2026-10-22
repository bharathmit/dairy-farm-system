package com.dairyfarm.breeding;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.breeding.BreedingViews.Family;
import com.dairyfarm.breeding.BreedingViews.MateCheck;
import com.dairyfarm.breeding.BreedingViews.NextTag;
import com.dairyfarm.security.FarmContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/animals")
@Tag(name = "Animals")
public class FamilyController {

    private final FamilyService family;
    private final FarmContext ctx;

    public FamilyController(FamilyService family, FarmContext ctx) {
        this.family = family;
        this.ctx = ctx;
    }

    @GetMapping("/next-tag")
    @Operation(summary = "Next free tag number, same prefix and padding (e.g. VD-021)")
    public NextTag nextTag(@PathVariable Long farmId, @RequestParam(required = false) String prefix) {
        return family.nextTag(ctx.farmId(), prefix);
    }

    @GetMapping("/{animalId}/family")
    @Operation(summary = "Parents, grandparents, siblings and offspring")
    public Family family(@PathVariable Long farmId, @PathVariable Long animalId) {
        return family.family(ctx.farmId(), animalId);
    }

    @GetMapping("/{animalId}/mate-check")
    @Operation(summary = "Inbreeding risk of a bull or semen batch for this female")
    public MateCheck mateCheck(@PathVariable Long farmId, @PathVariable Long animalId,
                               @RequestParam(required = false) Long sireAnimalId,
                               @RequestParam(required = false) Long semenItemId) {
        return family.mateCheck(ctx.farmId(), animalId, sireAnimalId, semenItemId);
    }
}
