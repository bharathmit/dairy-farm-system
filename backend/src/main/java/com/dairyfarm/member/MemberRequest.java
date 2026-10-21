package com.dairyfarm.member;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.dairyfarm.common.Sex;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Add or edit a team member.
 * login: NONE (no sign-in), PASSWORD (owner sets it now) or INVITE (email a link). On edit, leave
 * login null to keep the current sign-in; send PASSWORD with a new password to change it.
 */
public record MemberRequest(
        @NotBlank(message = "Enter a name") @Size(max = 120) String name,
        @NotNull(message = "Choose a role") Role role,
        @Email(message = "Enter a valid email") @Size(max = 254) String email,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()-]*$", message = "Use digits, spaces and + only") String phone,
        @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
        Sex sex,
        WageType wageType,
        @DecimalMin(value = "0", message = "Wage can't be negative") BigDecimal wageRate,
        LocalDate joinedOn,
        LoginType login,
        @Size(min = 8, max = 72, message = "Use 8 to 72 characters") String password) {
}
