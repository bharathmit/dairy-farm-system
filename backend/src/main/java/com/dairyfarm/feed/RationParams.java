package com.dairyfarm.feed;

import java.math.BigDecimal;
import java.util.List;

import com.dairyfarm.feed.FeedViews.RationCategory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One category's ration rule (JSONB in ration_rule). Missing values fall back to the category defaults
 * ({@link #defaults(RationCategory)}).
 */
public record RationParams(
        @DecimalMin(value = "1", message = "Use 1 to 20 %") @DecimalMax(value = "20", message = "Use 1 to 20 %") BigDecimal bwPct,
        @DecimalMin(value = "0", message = "Use 0 to 10 kg") @DecimalMax(value = "10", message = "Use 0 to 10 kg") BigDecimal concBaseKg,
        @DecimalMin(value = "0", message = "Use 0 to 2 kg") @DecimalMax(value = "2", message = "Use 0 to 2 kg") BigDecimal concPerLitreKg,
        @DecimalMin(value = "0", message = "Use 0 to 25 kg") @DecimalMax(value = "25", message = "Use 0 to 25 kg") BigDecimal maxConcKg,
        Long concItemId,
        @DecimalMin(value = "0", message = "Use 0 to 100 %") @DecimalMax(value = "100", message = "Use 0 to 100 %") BigDecimal greenSharePct,
        @Valid @Size(max = 20) List<Share> green,
        @Valid @Size(max = 20) List<Share> dry,
        @Valid @Size(max = 30) List<Limit> limits,
        @DecimalMin(value = "0.5", message = "Use 0.5 to 6 %") @DecimalMax(value = "6", message = "Use 0.5 to 6 %") BigDecimal dmIntakePct,
        @DecimalMin(value = "0", message = "Use 0 to 40 %") @DecimalMax(value = "40", message = "Use 0 to 40 %") BigDecimal cpPct,
        @DecimalMin(value = "0", message = "Use 0 to 100 %") @DecimalMax(value = "100", message = "Use 0 to 100 %") BigDecimal tdnPct,
        @DecimalMin(value = "0", message = "Use 0 to 5 %") @DecimalMax(value = "5", message = "Use 0 to 5 %") BigDecimal caPct,
        @DecimalMin(value = "0", message = "Use 0 to 5 %") @DecimalMax(value = "5", message = "Use 0 to 5 %") BigDecimal pPct) {

    public enum Stage { ANY, EARLY, MID, LATE }

    public record Share(@NotNull(message = "Pick an item") Long itemId,
                        @NotNull(message = "Enter the share")
                        @DecimalMin(value = "0", message = "Use 0 to 100 %") @DecimalMax(value = "100", message = "Use 0 to 100 %") BigDecimal sharePct) {
    }

    public record Limit(@NotNull(message = "Pick an item") Long itemId,
                        @NotNull(message = "Enter the limit")
                        @DecimalMin(value = "0", message = "Use 0 or more kg") BigDecimal maxKgPerDay,
                        Stage stage) {
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static RationParams base(String bw, String base, String perL, String max, String dm, String cp, String tdn) {
        return new RationParams(d(bw), d(base), d(perL), d(max), null, d("70"), List.of(), List.of(), List.of(),
                d(dm), d(cp), d(tdn), d("0.6"), d("0.4"));
    }

    public static RationParams defaults(RationCategory c) {
        return switch (c) {
            case MILKING -> base("10", "1.5", "0.4", "10", "3.0", "14", "65");
            case DRY -> base("10", "1.5", "0", "3", "3.0", "12", "60");
            case HEIFER -> base("10", "1.0", "0", "2", "2.5", "14", "62");
            case CALF -> base("10", "0.5", "0", "1.5", "2.5", "18", "70");
            case BULL -> base("8", "2", "0", "3", "3.0", "10", "58");
        };
    }

    /** This rule with the category defaults filled in. */
    public RationParams resolved(RationCategory c) {
        var d = defaults(c);
        return new RationParams(or(bwPct, d.bwPct), or(concBaseKg, d.concBaseKg), or(concPerLitreKg, d.concPerLitreKg),
                or(maxConcKg, d.maxConcKg), concItemId, or(greenSharePct, d.greenSharePct),
                green == null ? List.of() : green, dry == null ? List.of() : dry, limits == null ? List.of() : limits,
                or(dmIntakePct, d.dmIntakePct), or(cpPct, d.cpPct), or(tdnPct, d.tdnPct), or(caPct, d.caPct),
                or(pPct, d.pPct));
    }

    private static <T> T or(T v, T f) {
        return v != null ? v : f;
    }
}
