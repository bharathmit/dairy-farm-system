package com.dairyfarm.user;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.farm.FarmSummary;
import com.dairyfarm.farm.FarmQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
        AppUser u = users.current();
        return new MeResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getPreferences(),
                farms.farmsOf(u.getId()));
    }
}
