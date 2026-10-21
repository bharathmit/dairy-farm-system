package com.dairyfarm.animal;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.dairyfarm.animal.AnimalEnums.Acquisition;
import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.common.Sex;
import com.dairyfarm.common.Species;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Add or edit an animal. Parents can be an animal on the farm (damId / sireId) or just a tag or
 * AI bull code (damTag / sireTag); a tag that matches an animal on the farm is linked to it.
 */
public record AnimalRequest(
        @NotBlank(message = "Enter the ear tag number")
        @Size(max = 40, message = "Use up to 40 characters")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 ._/-]*$", message = "Use letters, numbers, spaces and . _ / -")
        String tag,
        @Size(max = 80) String name,
        @Size(max = 40) String nationalId,
        @NotNull(message = "Choose a species") Species species,
        @Size(max = 60) String breed,
        @NotNull(message = "Choose the sex") Sex sex,
        @PastOrPresent(message = "Date of birth can't be in the future") LocalDate dateOfBirth,
        @Size(max = 60) String colour,
        @DecimalMin(value = "0.1", message = "Enter a weight above 0") @DecimalMax("2000") BigDecimal birthWeightKg,
        @NotNull(message = "Choose a category") Category category,
        Long groupId,
        Long damId,
        @Size(max = 40) String damTag,
        Long sireId,
        @Size(max = 60) String sireTag,
        @NotNull(message = "Choose born on farm or purchased") Acquisition acquisitionType,
        @PastOrPresent(message = "Purchase date can't be in the future") LocalDate acquisitionDate,
        @DecimalMin(value = "0", message = "Price can't be negative") BigDecimal purchasePrice,
        @PastOrPresent(message = "Calving date can't be in the future") LocalDate lastCalvingDate,
        @DecimalMin(value = "0.1", message = "Enter a weight above 0") @DecimalMax("2000") BigDecimal currentWeightKg,
        @Size(max = 2000) String notes) {
}
