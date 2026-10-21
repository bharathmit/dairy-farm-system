package com.dairyfarm.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
        @NotBlank(message = "Enter your name") @Size(max = 120) String fullName,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()-]*$", message = "Use digits, spaces and + only") String phone) {
}
