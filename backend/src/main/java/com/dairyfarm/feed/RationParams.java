package com.dairyfarm.feed;

import java.math.BigDecimal;
import java.util.List;

import com.dairyfarm.feed.FeedViews.FeedGroup;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One feeding group's TMR ration rule (JSONB in ration_rule). Missing values fall back to the category defaults
 * ({@link #defaults(FeedGroup)}). Unknown keys (bwPct, greenSharePct from the old as-fed rule) are ignored.
 *
 * <p>Dry matter intake (DMI) = body weight × DMI %; the DMI % comes from the lactation stage (milking), the age
 * band (heifers, calves) or {@code dmIntakePct}. DMI is split green / dry / concentrate by the DM shares, and
 * each part's fresh kg = its DM ÷ the item's DM %.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RationParams(
        ConcMode concMode,
        @DecimalMin(value = "0", message = "Use 0 to 10 kg") @DecimalMax(value = "10", message = "Use 0 to 10 kg") BigDecimal concBaseKg,
        @DecimalMin(value = "0", message = "Use 0 to 2 kg") @DecimalMax(value = "2", message = "Use 0 to 2 kg") BigDecimal concPerLitreKg,
        @DecimalMin(value = "0", message = "Use 0 to 25 kg") @DecimalMax(value = "25", message = "Use 0 to 25 kg") BigDecimal maxConcKg,
        Long concItemId,
        @DecimalMin(value = "0", message = "Use 0 to 100 %") @DecimalMax(value = "100", message = "Use 0 to 100 %") BigDecimal greenDmPct,
        @DecimalMin(value = "0", message = "Use 0 to 100 %") @DecimalMax(value = "100", message = "Use 0 to 100 %") BigDecimal dryDmPct,
        @DecimalMin(value = "0", message = "Use 0 to 100 %") @DecimalMax(value = "100", message = "Use 0 to 100 %") BigDecimal concDmPct,
        @Valid @Size(max = 20) List<Share> green,
        @Valid @Size(max = 20) List<Share> dry,
        @Valid @Size(max = 30) List<Limit> limits,
        @DecimalMin(value = "0.5", message = "Use 0.5 to 6 %") @DecimalMax(value = "6", message = "Use 0.5 to 6 %") BigDecimal dmIntakePct,
        @DecimalMin(value = "0.5", message = "Use 0.5 to 6 %") @DecimalMax(value = "6", message = "Use 0.5 to 6 %") BigDecimal dmiEarlyPct,
        @DecimalMin(value = "0.5", message = "Use 0.5 to 6 %") @DecimalMax(value = "6", message = "Use 0.5 to 6 %") BigDecimal dmiMidPct,
        @DecimalMin(value = "0.5", message = "Use 0.5 to 6 %") @DecimalMax(value = "6", message = "Use 0.5 to 6 %") BigDecimal dmiLatePct,
        @DecimalMin(value = "0.5", message = "Use 0.5 to 6 %") @DecimalMax(value = "6", message = "Use 0.5 to 6 %") BigDecimal dmiAge3to6Pct,
        @DecimalMin(value = "0.5", message = "Use 0.5 to 6 %") @DecimalMax(value = "6", message = "Use 0.5 to 6 %") BigDecimal dmiAge6to12Pct,
        @DecimalMin(value = "0.5", message = "Use 0.5 to 6 %") @DecimalMax(value = "6", message = "Use 0.5 to 6 %") BigDecimal dmiAge12to24Pct,
        @DecimalMin(value = "0", message = "Use 0 to 100 L") @DecimalMax(value = "100", message = "Use 0 to 100 L") BigDecimal waterBaseL,
        @DecimalMin(value = "0", message = "Use 0 to 10 L") @DecimalMax(value = "10", message = "Use 0 to 10 L") BigDecimal waterPerLitreL,
        @DecimalMin(value = "0", message = "Use 0 to 40 %") @DecimalMax(value = "40", message = "Use 0 to 40 %") BigDecimal cpPct,
        @DecimalMin(value = "0", message = "Use 0 to 100 %") @DecimalMax(value = "100", message = "Use 0 to 100 %") BigDecimal tdnPct,
        @DecimalMin(value = "0", message = "Use 0 to 5 %") @DecimalMax(value = "5", message = "Use 0 to 5 %") BigDecimal caPct,
        @DecimalMin(value = "0", message = "Use 0 to 5 %") @DecimalMax(value = "5", message = "Use 0 to 5 %") BigDecimal pPct) {

    public enum Stage { ANY, EARLY, MID, LATE }

    /** How concentrate is worked out: MILK = base + milk × per litre; DMI = DMI × concentrate DM share ÷ DM. */
    public enum ConcMode { MILK, DMI }

    /**
     * A fodder item's share of its part's DM. With {@code fixedKg} the item is fed at that fresh kg (e.g. hydroponic
     * fodder 7 kg, azolla 1 kg) and its DM comes off the part first; the rest is split by share. Optional: an
     * inactive or excluded fixed item is skipped and the others fill the part.
     */
    public record Share(@NotNull(message = "Pick an item") Long itemId,
                        @NotNull(message = "Enter the share")
                        @DecimalMin(value = "0", message = "Use 0 to 100 %") @DecimalMax(value = "100", message = "Use 0 to 100 %") BigDecimal sharePct,
                        @DecimalMin(value = "0", message = "Use 0 to 50 kg") @DecimalMax(value = "50", message = "Use 0 to 50 kg") BigDecimal fixedKg) {

        public Share(Long itemId, BigDecimal sharePct) {
            this(itemId, sharePct, null);
        }
    }

    public record Limit(@NotNull(message = "Pick an item") Long itemId,
                        @NotNull(message = "Enter the limit")
                        @DecimalMin(value = "0", message = "Use 0 or more kg") BigDecimal maxKgPerDay,
                        Stage stage) {
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static RationParams base(ConcMode mode, String base, String perL, String max, String green, String dry,
                                     String conc, String dmi, String water, String cp, String tdn) {
        return new RationParams(mode, d(base), d(perL), d(max), null, d(green), d(dry), d(conc), List.of(), List.of(),
                List.of(), d(dmi), d("3.75"), d("3.25"), d("2.75"), d("3.00"), d("2.75"), d("2.50"), d(water), d("4"),
                d(cp), d(tdn), d("0.6"), d("0.4"));
    }

    /** Milking groups start from the old milking defaults, close-up / steaming-up from the dry ones. */
    public static RationParams defaults(FeedGroup g) {
        return switch (g) {
            case FRESH, EARLY, MID, LATE -> base(ConcMode.MILK, "1.5", "0.4", "10", "45", "15", "40", "3.25", "30", "14", "65");
            case CLOSE_UP, STEAM_UP, DRY -> base(ConcMode.DMI, "1.5", "0", "3", "45", "25", "30", "2.00", "30", "12", "60");
            case HEIFER -> base(ConcMode.DMI, "1.0", "0", "2", "45", "25", "30", "2.50", "20", "14", "62");
            case CALF -> base(ConcMode.DMI, "0.5", "0", "1.5", "40", "20", "40", "3.00", "10", "18", "70");
            case BULL -> base(ConcMode.DMI, "2", "0", "3", "45", "25", "30", "2.00", "30", "10", "58");
        };
    }

    /** This rule with the group defaults filled in. */
    public RationParams resolved(FeedGroup c) {
        var d = defaults(c);
        return new RationParams(or(concMode, d.concMode), or(concBaseKg, d.concBaseKg), or(concPerLitreKg, d.concPerLitreKg),
                or(maxConcKg, d.maxConcKg), concItemId, or(greenDmPct, d.greenDmPct), or(dryDmPct, d.dryDmPct),
                or(concDmPct, d.concDmPct), green == null ? List.of() : green, dry == null ? List.of() : dry,
                limits == null ? List.of() : limits, or(dmIntakePct, d.dmIntakePct), or(dmiEarlyPct, d.dmiEarlyPct),
                or(dmiMidPct, d.dmiMidPct), or(dmiLatePct, d.dmiLatePct), or(dmiAge3to6Pct, d.dmiAge3to6Pct),
                or(dmiAge6to12Pct, d.dmiAge6to12Pct), or(dmiAge12to24Pct, d.dmiAge12to24Pct), or(waterBaseL, d.waterBaseL),
                or(waterPerLitreL, d.waterPerLitreL), or(cpPct, d.cpPct), or(tdnPct, d.tdnPct), or(caPct, d.caPct),
                or(pPct, d.pPct));
    }

    private static <T> T or(T v, T f) {
        return v != null ? v : f;
    }
}
