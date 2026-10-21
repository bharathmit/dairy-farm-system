package com.dairyfarm.member;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/members")
@OwnerOnly
@Tag(name = "Team")
public class MemberController {

    private final MemberService members;
    private final FarmContext ctx;

    public MemberController(MemberService members, FarmContext ctx) {
        this.members = members;
        this.ctx = ctx;
    }

    public record StatusRequest(@NotNull Boolean active, LocalDate leftOn) {
    }

    @GetMapping
    @Operation(summary = "Owners and workers, active first")
    public List<MemberResponse> list(@PathVariable Long farmId) {
        return members.list(ctx.farmId());
    }

    @GetMapping("/{id}")
    public MemberResponse get(@PathVariable Long farmId, @PathVariable Long id) {
        return members.get(ctx.farmId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a member, optionally with a sign-in (owner-set password or email invite)")
    public MemberResponse create(@PathVariable Long farmId, @Valid @RequestBody MemberRequest req) {
        return members.create(ctx.farmId(), req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a member; change their role, wage or sign-in")
    public MemberResponse update(@PathVariable Long farmId, @PathVariable Long id,
                                 @Valid @RequestBody MemberRequest req) {
        return members.update(ctx.farmId(), id, req);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Mark a member inactive (left the farm) or active again")
    public MemberResponse setStatus(@PathVariable Long farmId, @PathVariable Long id,
                                    @Valid @RequestBody StatusRequest req) {
        return members.setActive(ctx.farmId(), id, req.active(), req.leftOn());
    }

    @PostMapping("/{id}/invite")
    @Operation(summary = "Send the invite email again")
    public MemberResponse resendInvite(@PathVariable Long farmId, @PathVariable Long id) {
        return members.resendInvite(ctx.farmId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member from this farm (their account is kept)")
    public void delete(@PathVariable Long farmId, @PathVariable Long id) {
        members.delete(ctx.farmId(), id);
    }
}
