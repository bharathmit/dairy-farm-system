package com.dairyfarm.animal;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.dairyfarm.animal.AnimalEnums.Status;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Record that an animal left the herd (SOLD, DEAD, TRANSFERRED) or bring it back (ACTIVE).
 * party is the buyer for a sale or where it went for a transfer.
 */
public record StatusRequest(
        @NotNull(message = "Choose a status") Status status,
        @PastOrPresent(message = "Date can't be in the future") LocalDate date,
        @DecimalMin(value = "0", message = "Price can't be negative") BigDecimal price,
        @Size(max = 80) String reason,
        @Size(max = 120) String party) {
}
