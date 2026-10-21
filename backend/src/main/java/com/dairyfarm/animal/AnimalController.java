package com.dairyfarm.animal;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.animal.AnimalEnums.Status;
import com.dairyfarm.animal.AnimalViews.AnimalDetail;
import com.dairyfarm.animal.AnimalViews.AnimalListItem;
import com.dairyfarm.animal.AnimalViews.HerdSummary;
import com.dairyfarm.animal.csv.AnimalCsvService;
import com.dairyfarm.animal.csv.AnimalCsvService.ImportResult;
import com.dairyfarm.animal.photo.AnimalPhotoService;
import com.dairyfarm.common.PageResponse;
import com.dairyfarm.common.Sex;
import com.dairyfarm.common.Species;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/animals")
@Tag(name = "Animals")
public class AnimalController {

    private final AnimalService animals;
    private final AnimalPhotoService photos;
    private final AnimalCsvService csv;
    private final FarmContext ctx;

    public AnimalController(AnimalService animals, AnimalPhotoService photos, AnimalCsvService csv, FarmContext ctx) {
        this.animals = animals;
        this.photos = photos;
        this.csv = csv;
        this.ctx = ctx;
    }

    public record GroupMove(Long groupId) {
    }

    public record BulkDelete(@NotEmpty(message = "Choose at least one animal") @Size(max = 500) List<Long> ids) {
    }

    public record Deleted(int deleted) {
    }

    // ---------- read (owners and workers) ----------

    @GetMapping
    @Operation(summary = "Search and filter the herd. No status filter means every status.")
    public PageResponse<AnimalListItem> list(
            @PathVariable Long farmId,
            @Parameter(description = "Tag, name or national ID contains") @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Species> species,
            @RequestParam(required = false) List<Category> category,
            @RequestParam(required = false) List<Status> status,
            @RequestParam(required = false) Sex sex,
            @RequestParam(required = false) Long groupId,
            @Parameter(description = "Only animals without a group") @RequestParam(required = false) Boolean noGroup,
            @Parameter(description = "tag, name, newest or oldest") @RequestParam(defaultValue = "tag") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var query = new AnimalQuery(q, species, category, status, sex, groupId, noGroup, sort);
        return animals.list(ctx.farmId(), query, page, size);
    }

    @GetMapping("/summary")
    @Operation(summary = "Active herd size by category, and counts by status")
    public HerdSummary summary(@PathVariable Long farmId) {
        return animals.summary(ctx.farmId());
    }

    @GetMapping("/by-tag/{tag}")
    @Operation(summary = "Find an animal by its ear tag (QR / tag scan)")
    public AnimalDetail byTag(@PathVariable Long farmId, @PathVariable String tag) {
        return animals.getByTag(ctx.farmId(), tag, ctx.isOwner());
    }

    @GetMapping("/{id}")
    public AnimalDetail get(@PathVariable Long farmId, @PathVariable Long id) {
        return animals.get(ctx.farmId(), id, ctx.isOwner());
    }

    // ---------- write (owners) ----------

    @PostMapping
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an animal")
    public AnimalDetail create(@PathVariable Long farmId, @Valid @RequestBody AnimalRequest req) {
        return animals.create(ctx.farmId(), req);
    }

    @PutMapping("/{id}")
    @OwnerOnly
    @Operation(summary = "Edit an animal (species can't change)")
    public AnimalDetail update(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody AnimalRequest req) {
        return animals.update(ctx.farmId(), id, req);
    }

    @PatchMapping("/{id}/status")
    @OwnerOnly
    @Operation(summary = "Mark sold, dead or transferred (or active again)")
    public AnimalDetail changeStatus(@PathVariable Long farmId, @PathVariable Long id,
                                     @Valid @RequestBody StatusRequest req) {
        return animals.changeStatus(ctx.farmId(), id, req);
    }

    @PatchMapping("/{id}/group")
    @OwnerOnly
    @Operation(summary = "Move to a group, or out of any group with groupId null")
    public AnimalDetail moveToGroup(@PathVariable Long farmId, @PathVariable Long id, @RequestBody GroupMove req) {
        return animals.moveToGroup(ctx.farmId(), id, req.groupId());
    }

    @DeleteMapping("/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an animal and its photos. Offspring keep its tag as text.")
    public void delete(@PathVariable Long farmId, @PathVariable Long id) {
        animals.delete(ctx.farmId(), id);
    }

    @PostMapping("/bulk-delete")
    @OwnerOnly
    public Deleted bulkDelete(@PathVariable Long farmId, @Valid @RequestBody BulkDelete req) {
        return new Deleted(animals.deleteMany(ctx.farmId(), req.ids()));
    }

    // ---------- photos ----------

    @PostMapping(path = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a photo (JPG, PNG or WebP, up to 5 MB; 3 per animal)")
    public AnimalDetail addPhoto(@PathVariable Long farmId, @PathVariable Long id,
                                 @RequestPart("file") MultipartFile file) {
        photos.add(ctx.farmId(), id, file);
        return animals.get(ctx.farmId(), id, ctx.isOwner());
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @OwnerOnly
    public AnimalDetail removePhoto(@PathVariable Long farmId, @PathVariable Long id, @PathVariable String photoId) {
        photos.remove(ctx.farmId(), id, photoId);
        return animals.get(ctx.farmId(), id, true);
    }

    // ---------- CSV ----------

    @GetMapping(path = "/import/template", produces = "text/csv")
    @OwnerOnly
    @Operation(summary = "CSV template for importing a herd")
    public ResponseEntity<byte[]> template(@PathVariable Long farmId) {
        return csvFile("herd-template.csv", csv.template());
    }

    @GetMapping(path = "/export.csv", produces = "text/csv")
    @OwnerOnly
    @Operation(summary = "Download the whole herd as CSV (same columns as the template)")
    public ResponseEntity<byte[]> export(@PathVariable Long farmId) {
        return csvFile("herd-" + LocalDate.now() + ".csv", csv.export(ctx.farmId()));
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @OwnerOnly
    @Operation(summary = "Import animals from CSV. dryRun=true (default) checks the file without saving.")
    public ImportResult importCsv(@PathVariable Long farmId, @RequestPart("file") MultipartFile file,
                                  @RequestParam(defaultValue = "true") boolean dryRun) {
        return csv.importCsv(ctx.farmId(), file, dryRun);
    }

    private static ResponseEntity<byte[]> csvFile(String name, String body) {
        // BOM so Excel opens Tamil names and the ₹ sign correctly.
        byte[] bytes = ("﻿" + body).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }
}
