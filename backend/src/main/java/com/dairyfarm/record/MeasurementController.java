package com.dairyfarm.record;

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

import com.dairyfarm.record.MeasurementViews.Growth;
import com.dairyfarm.record.MeasurementViews.Measurement;
import com.dairyfarm.record.MeasurementViews.MeasurementRequest;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/farms/{farmId}")
@Tag(name = "Weight & BCS")
public class MeasurementController {

    private final MeasurementService measurements;
    private final FarmContext ctx;

    public MeasurementController(MeasurementService measurements, FarmContext ctx) {
        this.measurements = measurements;
        this.ctx = ctx;
    }

    @GetMapping("/animals/{animalId}/measurements")
    @Operation(summary = "Weight, height and BCS history with gain per day")
    public Growth growth(@PathVariable Long farmId, @PathVariable Long animalId) {
        return measurements.growth(ctx.farmId(), animalId);
    }

    @PostMapping("/animals/{animalId}/measurements")
    @ResponseStatus(HttpStatus.CREATED)
    public Measurement create(@PathVariable Long farmId, @PathVariable Long animalId,
                              @Valid @RequestBody MeasurementRequest req) {
        return measurements.create(ctx.farmId(), animalId, req);
    }

    @PutMapping("/measurements/{id}")
    public Measurement update(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody MeasurementRequest req) {
        return measurements.update(ctx.farmId(), id, req);
    }

    @DeleteMapping("/measurements/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long farmId, @PathVariable Long id) {
        measurements.delete(ctx.farmId(), id);
    }
}
