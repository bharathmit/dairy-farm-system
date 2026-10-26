package com.dairyfarm.feed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.dairyfarm.feed.FeedViews.FeedGroup;
import com.dairyfarm.feed.FeedViews.RationCategory;

/**
 * Places an animal in its feeding group on a date (docs/api-feed.md, "Feeding groups"). Pure calculation, checked in
 * this order so each animal lands in exactly one group:
 * <ol>
 *   <li>Bull → BULL.</li>
 *   <li>Milking cow by days in milk: FRESH 1–21, EARLY 22–100, MID 101–200, LATE 201+ (no calving date → MID).</li>
 *   <li>Confirmed pregnant (dry cow or heifer) by days to the due date: CLOSE_UP ≤ 21 (also overdue), STEAM_UP ≤ 60.</li>
 *   <li>Dry cow → DRY.</li>
 *   <li>Young female: under 9 months → CALF; ready for first service (age or known weight reaches the breeding
 *       setting) → DRY ("Dry & mature heifers"); otherwise → HEIFER (growing heifers).</li>
 * </ol>
 * Male calves stay CALF. Estimated weights are not used for maturity.
 */
public final class FeedGroups {

    static final int FRESH_DAYS = 21, EARLY_DAYS = 100, MID_DAYS = 200;
    static final int CLOSE_UP_DAYS = 21, STEAM_UP_DAYS = 60;
    static final int CALF_MONTHS = 9;

    private FeedGroups() {
    }

    /** Plain name shown to the farm (same as the app's feeding group labels). */
    public static String label(FeedGroup g) {
        return switch (g) {
            case FRESH -> "Fresh cow";
            case EARLY -> "Early lactation";
            case MID -> "Mid lactation";
            case LATE -> "Late lactation";
            case CLOSE_UP -> "Close-up";
            case STEAM_UP -> "Steaming-up";
            case DRY -> "Dry & mature heifers";
            case HEIFER -> "Growing heifers";
            case CALF -> "Calves";
            case BULL -> "Bulls";
        };
    }

    /**
     * @param dueDate        expected calving of a confirmed pregnancy not yet calved, or null
     * @param knownWeightKg  measured or recorded weight (not an estimate), or null
     * @param matureMonths   breeding setting: heifer first service age (months)
     * @param matureKg       breeding setting: heifer first service weight (kg)
     */
    public static FeedGroup of(RationCategory category, String sex, LocalDate dateOfBirth, LocalDate lastCalvingDate,
                               LocalDate dueDate, BigDecimal knownWeightKg, LocalDate date, int matureMonths,
                               BigDecimal matureKg) {
        if (category == RationCategory.BULL) {
            return FeedGroup.BULL;
        }
        if (category == RationCategory.MILKING) {
            if (lastCalvingDate == null || date.isBefore(lastCalvingDate)) {
                return FeedGroup.MID;
            }
            long dim = ChronoUnit.DAYS.between(lastCalvingDate, date) + 1;
            return dim <= FRESH_DAYS ? FeedGroup.FRESH : dim <= EARLY_DAYS ? FeedGroup.EARLY
                    : dim <= MID_DAYS ? FeedGroup.MID : FeedGroup.LATE;
        }
        if (dueDate != null) {
            long toDue = ChronoUnit.DAYS.between(date, dueDate);
            if (toDue <= CLOSE_UP_DAYS) {
                return FeedGroup.CLOSE_UP;
            }
            if (toDue <= STEAM_UP_DAYS) {
                return FeedGroup.STEAM_UP;
            }
            return FeedGroup.DRY;
        }
        if (category == RationCategory.DRY) {
            return FeedGroup.DRY;
        }
        // CALF / HEIFER: young stock
        if ("MALE".equalsIgnoreCase(sex) || "M".equalsIgnoreCase(sex)) {
            return FeedGroup.CALF;
        }
        Integer age = dateOfBirth == null || date.isBefore(dateOfBirth) ? null
                : (int) ChronoUnit.MONTHS.between(dateOfBirth, date);
        if (age != null && age < CALF_MONTHS) {
            return FeedGroup.CALF;
        }
        if (age == null && category == RationCategory.CALF) {
            return FeedGroup.CALF;
        }
        boolean mature = (age != null && age >= matureMonths)
                || (knownWeightKg != null && matureKg != null && knownWeightKg.compareTo(matureKg) >= 0);
        return mature ? FeedGroup.DRY : FeedGroup.HEIFER;
    }
}
