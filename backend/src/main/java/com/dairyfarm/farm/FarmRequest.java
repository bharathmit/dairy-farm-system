package com.dairyfarm.farm;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FarmRequest(
        @NotBlank(message = "Enter the farm name") @Size(max = 120) String name,
        @Size(max = 120) String village,
        @Size(max = 120) String district,
        @Size(max = 120) String state,
        @Size(max = 120) String country,
        @DecimalMin(value = "-90") @DecimalMax(value = "90") BigDecimal latitude,
        @DecimalMin(value = "-180") @DecimalMax(value = "180") BigDecimal longitude) {
}
