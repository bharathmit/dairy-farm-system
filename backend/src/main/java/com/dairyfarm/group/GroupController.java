package com.dairyfarm.group;

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

import com.dairyfarm.group.GroupViews.GroupDetail;
import com.dairyfarm.group.GroupViews.GroupSummary;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/groups")
@Tag(name = "Groups")
public class GroupController {

    private final GroupService groups;
    private final FarmContext ctx;

    public GroupController(GroupService groups, FarmContext ctx) {
        this.groups = groups;
        this.ctx = ctx;
    }

    public record GroupAnimals(@NotNull @Size(max = 1000) List<Long> animalIds) {
    }

    @GetMapping
    @Operation(summary = "Groups with their animal counts")
    public List<GroupSummary> list(@PathVariable Long farmId) {
        return groups.list(ctx.farmId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "A group and the animals in it")
    public GroupDetail get(@PathVariable Long farmId, @PathVariable Long id) {
        return groups.get(ctx.farmId(), id);
    }

    @PostMapping
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDetail create(@PathVariable Long farmId, @Valid @RequestBody GroupRequest req) {
        return groups.create(ctx.farmId(), req);
    }

    @PutMapping("/{id}")
    @OwnerOnly
    @Operation(summary = "Edit a group (species can't change)")
    public GroupDetail update(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody GroupRequest req) {
        return groups.update(ctx.farmId(), id, req);
    }

    @PutMapping("/{id}/animals")
    @OwnerOnly
    @Operation(summary = "Set which animals are in the group (others leave it)")
    public GroupDetail setAnimals(@PathVariable Long farmId, @PathVariable Long id,
                                  @Valid @RequestBody GroupAnimals req) {
        return groups.setAnimals(ctx.farmId(), id, req.animalIds());
    }

    @DeleteMapping("/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a group; its animals stay on the farm without a group")
    public void delete(@PathVariable Long farmId, @PathVariable Long id) {
        groups.delete(ctx.farmId(), id);
    }
}
