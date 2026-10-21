package com.dairyfarm.group;

import com.dairyfarm.common.Species;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GroupRequest(
        @NotBlank(message = "Enter a group name") @Size(max = 80) String name,
        @NotNull(message = "Choose a species") Species species,
        @Size(max = 120) String location,
        GroupPurpose purpose,
        Long managerMemberId,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Use a colour like #1F5C3A") String colour,
        @Size(max = 2000) String notes) {
}
