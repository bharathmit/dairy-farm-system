package com.dairyfarm.milk;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * How a cow's milk is graded (stored as JSONB on farm, owner-editable):
 * <ol>
 * <li>Expected litres per day from a lactation curve: rises from {@code startPct} of the peak on day 1 to the
 * peak on {@code peakDay}, then falls {@code fallPctPerMonth} each month. The peak is the cow's best day in her
 * last lactation (when {@code basis} is OWN_LACTATION and we have it) or the breed value.</li>
 * <li>Grade from actual ÷ expected: A ≥ {@code bandA} %, B ≥ {@code bandB} %, C ≥ {@code bandC} %, else D.</li>
 * <li>Each failed quality check on a tested day drops the grade by one.</li>
 * </ol>
 * Missing values fall back to {@link #DEFAULTS}.
 */
public record MilkGradingRules(
        Basis basis,
        @Valid @Size(max = 30, message = "Up to 30 breeds") List<BreedPeak> breeds,
        @Valid BreedPeak otherBreeds,
        @Min(value = 30, message = "Use 30 to 100") @Max(value = 100, message = "Use 30 to 100") Integer startPct,
        @DecimalMin(value = "0.0", message = "Use 0 to 30") @DecimalMax(value = "30.0", message = "Use 0 to 30")
        BigDecimal fallPctPerMonth,
        @Min(value = 40, message = "Use 40 to 100") @Max(value = 100, message = "Use 40 to 100") Integer firstCalverPct,
        @Min(value = 150, message = "Use 150 to 600") @Max(value = 600, message = "Use 150 to 600") Integer lactationDays,
        @Min(value = 1, message = "Use 1 to 200") @Max(value = 200, message = "Use 1 to 200") Integer bandA,
        @Min(value = 1, message = "Use 1 to 200") @Max(value = 200, message = "Use 1 to 200") Integer bandB,
        @Min(value = 1, message = "Use 1 to 200") @Max(value = 200, message = "Use 1 to 200") Integer bandC,
        @Valid Check minFatPct,
        @Valid Check minSnfPct,
        @Valid Check maxSccK,
        @Min(value = 5, message = "Use 5 to 80") @Max(value = 80, message = "Use 5 to 80") Integer dropAlertPct) {

    public enum Basis { OWN_LACTATION, BREED }

    /** Peak litres per day and the lactation day it is reached, for one breed (matched by name, ignoring case). */
    public record BreedPeak(
            @NotBlank(message = "Enter the breed") @Size(max = 60) String breed,
            @NotNull(message = "Enter the peak litres")
            @DecimalMin(value = "0.5", message = "Use 0.5 to 80 L") @DecimalMax(value = "80", message = "Use 0.5 to 80 L")
            BigDecimal peakLitres,
            @NotNull(message = "Enter the peak day") @Min(value = 10, message = "Use day 10 to 150")
            @Max(value = 150, message = "Use day 10 to 150") Integer peakDay) {
    }

    /** A quality limit that, when enabled and broken on a tested day, drops the grade one step. */
    public record Check(@NotNull Boolean enabled,
                        @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal limit) {
    }

    public static final MilkGradingRules DEFAULTS = new MilkGradingRules(
            Basis.OWN_LACTATION,
            List.of(peak("HF", "18", 50), peak("Jersey", "12", 45), peak("Crossbred", "14", 50),
                    peak("Gir", "8", 40), peak("Sahiwal", "8", 40), peak("Murrah", "10", 45)),
            peak("Other", "10", 50),
            70, new BigDecimal("8"), 75, 305,
            100, 90, 75,
            new Check(true, new BigDecimal("3.2")),
            new Check(true, new BigDecimal("8.0")),
            new Check(true, new BigDecimal("200")),
            20);

    private static BreedPeak peak(String breed, String litres, int day) {
        return new BreedPeak(breed, new BigDecimal(litres), day);
    }

    public MilkGradingRules resolved() {
        var d = DEFAULTS;
        return new MilkGradingRules(or(basis, d.basis), or(breeds, d.breeds), or(otherBreeds, d.otherBreeds),
                or(startPct, d.startPct), or(fallPctPerMonth, d.fallPctPerMonth), or(firstCalverPct, d.firstCalverPct),
                or(lactationDays, d.lactationDays), or(bandA, d.bandA), or(bandB, d.bandB), or(bandC, d.bandC),
                or(minFatPct, d.minFatPct), or(minSnfPct, d.minSnfPct), or(maxSccK, d.maxSccK),
                or(dropAlertPct, d.dropAlertPct));
    }

    /** The peak for a breed: exact name, then a breed whose name appears in the animal's breed, else other breeds. */
    public BreedPeak peakFor(String animalBreed) {
        var r = resolved();
        if (animalBreed != null && !animalBreed.isBlank()) {
            String b = animalBreed.trim().toLowerCase();
            for (BreedPeak p : r.breeds) {
                if (p.breed().trim().equalsIgnoreCase(b)) {
                    return p;
                }
            }
            boolean cross = b.contains("×") || b.contains(" x ") || b.contains("cross");
            for (BreedPeak p : r.breeds) {
                if (cross && p.breed().toLowerCase().startsWith("cross")) {
                    return p;
                }
            }
            for (BreedPeak p : r.breeds) {
                if (!cross && b.contains(p.breed().trim().toLowerCase())) {
                    return p;
                }
            }
        }
        return r.otherBreeds;
    }

    private static <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
