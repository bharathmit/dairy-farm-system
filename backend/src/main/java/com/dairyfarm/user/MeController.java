package com.dairyfarm.user;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.farm.FarmQueryService;
import com.dairyfarm.farm.FarmSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Me")
public class MeController {

    private final UserService users;
    private final FarmQueryService farms;

    public MeController(UserService users, FarmQueryService farms) {
        this.users = users;
        this.farms = farms;
    }

    public record MeResponse(UUID id, String fullName, String email, String phone,
                             UserPreferences preferences, List<FarmSummary> farms) {
    }

    @GetMapping
    @Operation(summary = "The signed-in person, their preferences and the farms they belong to")
    public MeResponse me() {
        return toResponse(users.current());
    }

    @PutMapping
    @Operation(summary = "Update my name and phone")
    public MeResponse updateProfile(@Valid @RequestBody ProfileRequest req) {
        return toResponse(users.updateProfile(req));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update my theme and language")
    public UserPreferences updatePreferences(@RequestBody UserPreferences prefs) {
        return users.updatePreferences(prefs).getPreferences();
    }

    private MeResponse toResponse(AppUser u) {
        return new MeResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getPreferences(),
                farms.farmsOf(u.getId()));
    }
}
