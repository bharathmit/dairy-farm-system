package com.dairyfarm.equipment;

import java.util.List;

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

import com.dairyfarm.equipment.EquipmentViews.*;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/equipment")
@Tag(name = "Equipment")
@OwnerOnly
public class EquipmentController {

    private final EquipmentService equipment;
    private final FarmContext ctx;

    public EquipmentController(EquipmentService equipment, FarmContext ctx) {
        this.equipment = equipment;
        this.ctx = ctx;
    }

    @GetMapping
    public EquipmentList list(@PathVariable Long farmId) {
        return equipment.list(ctx.farmId());
    }

    @GetMapping("/{id}")
    public Equipment get(@PathVariable Long farmId, @PathVariable Long id) {
        return equipment.get(ctx.farmId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Equipment create(@PathVariable Long farmId, @Valid @RequestBody EquipmentRequest req) {
        return equipment.create(ctx.farmId(), req);
    }

    @PutMapping("/{id}")
    public Equipment update(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody EquipmentRequest req) {
        return equipment.update(ctx.farmId(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long farmId, @PathVariable Long id) {
        equipment.delete(ctx.farmId(), id);
    }

    @PostMapping("/{id}/services")
    @ResponseStatus(HttpStatus.CREATED)
    public Equipment addService(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody ServiceRequest req) {
        return equipment.addService(ctx.farmId(), id, req);
    }

    @GetMapping("/{id}/services")
    public List<EquipmentViews.Service> services(@PathVariable Long farmId, @PathVariable Long id) {
        return equipment.services(ctx.farmId(), id);
    }
}
