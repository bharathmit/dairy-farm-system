package com.dairyfarm.breeding;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Breeding and calf-rearing rules (JSONB on farm, owner-editable). Missing values fall back to {@link #DEFAULTS}.
 */
public record BreedingSettings(
        Map<String, @Min(value = 100, message = "Use 100 to 400 days") @Max(value = 400, message = "Use 100 to 400 days") Integer> gestationDays,
        @Min(value = 15, message = "Use 15 to 30 days") @Max(value = 30, message = "Use 15 to 30 days") Integer heatCycleDays,
        @Min(value = 0, message = "Use 0 to 150 days") @Max(value = 150, message = "Use 0 to 150 days") Integer voluntaryWaitDays,
        @Min(value = 20, message = "Use 20 to 120 days") @Max(value = 120, message = "Use 20 to 120 days") Integer pdAfterDays,
        @Min(value = 0, message = "Use 0 to 120 days") @Max(value = 120, message = "Use 0 to 120 days") Integer dryOffDaysBeforeDue,
        @Min(value = 30, message = "Use 30 to 300 days") @Max(value = 300, message = "Use 30 to 300 days") Integer targetDaysOpen,
        @Min(value = 60, message = "Use 60 to 500 days") @Max(value = 500, message = "Use 60 to 500 days") Integer sellDaysOpen,
        @Min(value = 1, message = "Use 1 to 10") @Max(value = 10, message = "Use 1 to 10") Integer sellFailedServices,
        @Min(value = 6, message = "Use 6 to 36 months") @Max(value = 36, message = "Use 6 to 36 months") Integer heiferFirstServiceMonths,
        @DecimalMin(value = "50", message = "Use 50 to 600 kg") @DecimalMax(value = "600", message = "Use 50 to 600 kg") BigDecimal heiferFirstServiceKg,
        @Min(value = 1, message = "Use 1 to 12 hours") @Max(value = 12, message = "Use 1 to 12 hours") Integer colostrumHours,
        @Valid @Size(min = 1, max = 20, message = "Use 1 to 20 steps") List<TaperStep> calfTaper,
        @Min(value = 30, message = "Use 30 to 240 days") @Max(value = 240, message = "Use 30 to 240 days") Integer weanMinDays,
        @DecimalMin(value = "0.1", message = "Use 0.1 to 5 kg") @DecimalMax(value = "5", message = "Use 0.1 to 5 kg") BigDecimal weanStarterKg,
        @Min(value = 1, message = "Use 1 to 14 days") @Max(value = 14, message = "Use 1 to 14 days") Integer weanStarterDays,
        @Min(value = 0, message = "Use 0 to 365 days") @Max(value = 365, message = "Use 0 to 365 days") Integer maleSellFromDay,
        @Min(value = 1, message = "Use 1 to 365 days") @Max(value = 365, message = "Use 1 to 365 days") Integer maleSellToDay) {

    /** Milk and starter for calves of an age range (days, inclusive). */
    public record TaperStep(
            @NotNull(message = "Enter the first day") @Min(value = 0, message = "Use day 0 to 365") @Max(value = 365, message = "Use day 0 to 365") Integer fromDay,
            @NotNull(message = "Enter the last day") @Min(value = 0, message = "Use day 0 to 365") @Max(value = 365, message = "Use day 0 to 365") Integer toDay,
            @NotNull(message = "Enter the litres") @DecimalMin(value = "0", message = "Use 0 to 15 L") @DecimalMax(value = "15", message = "Use 0 to 15 L") BigDecimal milkLitres,
            @Size(max = 40) String starter) {
    }

    public static final BreedingSettings DEFAULTS = new BreedingSettings(
            gestation(),
            21, 45, 45, 60, 90, 150, 3, 14, new BigDecimal("250"), 2,
            List.of(step(0, 14, "3.0", "—"), step(15, 30, "3.0", "offer free"), step(31, 60, "3.5", "0.3–0.6 kg"),
                    step(61, 80, "2.0", "0.7–1.0 kg"), step(81, 90, "1.0", "≥ 1 kg")),
            90, new BigDecimal("1.0"), 3, 30, 90);

    private static Map<String, Integer> gestation() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("COW", 283);
        m.put("BUFFALO", 310);
        m.put("GOAT", 150);
        m.put("SHEEP", 150);
        return m;
    }

    private static TaperStep step(int from, int to, String litres, String starter) {
        return new TaperStep(from, to, new BigDecimal(litres), starter);
    }

    public BreedingSettings resolved() {
        var d = DEFAULTS;
        Map<String, Integer> g = new LinkedHashMap<>(d.gestationDays);
        if (gestationDays != null) {
            gestationDays.forEach((k, v) -> {
                if (k != null && v != null) {
                    g.put(k.toUpperCase(), v);
                }
            });
        }
        return new BreedingSettings(g, or(heatCycleDays, d.heatCycleDays), or(voluntaryWaitDays, d.voluntaryWaitDays),
                or(pdAfterDays, d.pdAfterDays), or(dryOffDaysBeforeDue, d.dryOffDaysBeforeDue),
                or(targetDaysOpen, d.targetDaysOpen), or(sellDaysOpen, d.sellDaysOpen),
                or(sellFailedServices, d.sellFailedServices), or(heiferFirstServiceMonths, d.heiferFirstServiceMonths),
                or(heiferFirstServiceKg, d.heiferFirstServiceKg), or(colostrumHours, d.colostrumHours),
                calfTaper == null || calfTaper.isEmpty() ? d.calfTaper : calfTaper, or(weanMinDays, d.weanMinDays),
                or(weanStarterKg, d.weanStarterKg), or(weanStarterDays, d.weanStarterDays),
                or(maleSellFromDay, d.maleSellFromDay), or(maleSellToDay, d.maleSellToDay));
    }

    /** Gestation for a species (cow length when the species has none). */
    public int gestationFor(String species) {
        var g = resolved().gestationDays;
        return g.getOrDefault(species, g.getOrDefault("COW", 283));
    }

    /** The taper step for a calf's age; past the last step, the last one. */
    public TaperStep taperFor(Integer ageDays) {
        var steps = resolved().calfTaper;
        if (ageDays == null) {
            return steps.get(0);
        }
        for (TaperStep s : steps) {
            if (ageDays >= s.fromDay() && ageDays <= s.toDay()) {
                return s;
            }
        }
        return ageDays < steps.get(0).fromDay() ? steps.get(0) : new TaperStep(steps.get(steps.size() - 1).toDay() + 1, 365,
                BigDecimal.ZERO, "weaned");
    }

    private static <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
