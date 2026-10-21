package com.dairyfarm.record;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.dairyfarm.animal.AnimalEnums.Category;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MeasurementViews {

    private MeasurementViews() {
    }

    public enum Method { SCALE, TAPE, ESTIMATE }

    public record MeasurementRequest(
            @NotNull(message = "Pick the date") LocalDate date,
            @NotNull(message = "Pick how it was measured") Method method,
            @DecimalMin(value = "1", message = "Use 1 to 2000 kg") @DecimalMax(value = "2000", message = "Use 1 to 2000 kg") BigDecimal weightKg,
            @DecimalMin(value = "20", message = "Use 20 to 250 cm") @DecimalMax(value = "250", message = "Use 20 to 250 cm") BigDecimal heightCm,
            @DecimalMin(value = "30", message = "Use 30 to 300 cm") @DecimalMax(value = "300", message = "Use 30 to 300 cm") BigDecimal heartGirthCm,
            @DecimalMin(value = "1.0", message = "BCS is 1 to 5") @DecimalMax(value = "5.0", message = "BCS is 1 to 5") BigDecimal bcs,
            @Size(max = 2000) String notes) {
    }

    public record Measurement(Long id, Long animalId, LocalDate date, Method method, BigDecimal weightKg,
                              BigDecimal heightCm, BigDecimal heartGirthCm, BigDecimal bcs, String notes,
                              BigDecimal gainKg, BigDecimal adgKg) {
    }

    public record AnimalInfo(Long id, String tag, String name, Category category, LocalDate dateOfBirth) {
    }

    public record Latest(LocalDate date, BigDecimal weightKg, BigDecimal heightCm, BigDecimal bcs) {
    }

    public record Growth(AnimalInfo animal, Latest latest, BigDecimal adg90dKg, List<Measurement> items) {
    }
}
