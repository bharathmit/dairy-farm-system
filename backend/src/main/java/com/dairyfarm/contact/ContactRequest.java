package com.dairyfarm.contact;

import java.math.BigDecimal;
import java.util.List;

import com.dairyfarm.contact.Contact.PayEvery;
import com.dairyfarm.contact.Contact.Role;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank(message = "Enter a name") @Size(max = 120, message = "Keep the name under 120 characters")
        String name,
        @NotEmpty(message = "Pick at least one type") List<Role> roles,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()-]*$", message = "Use digits, spaces and + only") String phone,
        @Size(max = 120) String village,
        @DecimalMin(value = "0.0", message = "Rate can't be negative")
        @DecimalMax(value = "1000.0", message = "Rate looks too high") BigDecimal milkRate,
        PayEvery payEvery,
        @Size(max = 2000) String notes,
        Boolean active) {
}
