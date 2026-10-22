package com.dairyfarm.procurement;

import java.util.List;

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

import com.dairyfarm.procurement.ProcurementViews.*;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/procurement")
@Tag(name = "Procurement")
@OwnerOnly
public class ProcurementController {

    private final ProcurementService procurement;
    private final FarmContext ctx;

    public ProcurementController(ProcurementService procurement, FarmContext ctx) {
        this.procurement = procurement;
        this.ctx = ctx;
    }

    @GetMapping
    @Operation(summary = "Procurement notes with counts and low-stock suggestions")
    public Board board(@PathVariable Long farmId, @RequestParam(required = false) NoteStatus status) {
        return procurement.board(ctx.farmId(), status);
    }

    @GetMapping("/{id}")
    public Note get(@PathVariable Long farmId, @PathVariable Long id) {
        return procurement.get(ctx.farmId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Note create(@PathVariable Long farmId, @Valid @RequestBody NoteRequest req) {
        return procurement.create(ctx.farmId(), req);
    }

    @PutMapping("/{id}")
    public Note update(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        return procurement.update(ctx.farmId(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long farmId, @PathVariable Long id) {
        procurement.delete(ctx.farmId(), id);
    }

    @PostMapping("/{id}/order")
    public Note order(@PathVariable Long farmId, @PathVariable Long id, @RequestBody(required = false) OrderRequest req) {
        return procurement.order(ctx.farmId(), id, req);
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Mark received: adds stock and an expense (or a manual expense when no item)")
    public Note receive(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody ReceiveRequest req) {
        return procurement.receive(ctx.farmId(), id, req);
    }

    @PostMapping("/from-feed-need")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Plan notes for the feed shortfalls in the next days")
    public List<Note> fromFeedNeed(@PathVariable Long farmId, @RequestParam(defaultValue = "7") int days) {
        return procurement.fromFeedNeed(ctx.farmId(), Math.min(Math.max(days, 1), 60));
    }
}
