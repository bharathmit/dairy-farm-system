package com.dairyfarm.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.dairyfarm.feed.FeedViews.FeedGroup;
import com.dairyfarm.feed.FeedViews.RationCategory;

class FeedGroupsTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 26);
    private static final BigDecimal MATURE_KG = new BigDecimal("250");

    private static FeedGroup group(RationCategory cat, String sex, LocalDate born, LocalDate calved, LocalDate due, String kg) {
        return FeedGroups.of(cat, sex, born, calved, due, kg == null ? null : new BigDecimal(kg), DATE, 14, MATURE_KG);
    }

    private static FeedGroup milking(int dim) {
        return group(RationCategory.MILKING, "FEMALE", null, DATE.minusDays(dim - 1), null, null);
    }

    @Test
    void milkingCowsByDaysInMilk() {
        assertThat(milking(1)).isEqualTo(FeedGroup.FRESH);
        assertThat(milking(21)).isEqualTo(FeedGroup.FRESH);
        assertThat(milking(22)).isEqualTo(FeedGroup.EARLY);
        assertThat(milking(100)).isEqualTo(FeedGroup.EARLY);
        assertThat(milking(101)).isEqualTo(FeedGroup.MID);
        assertThat(milking(200)).isEqualTo(FeedGroup.MID);
        assertThat(milking(201)).isEqualTo(FeedGroup.LATE);
        assertThat(group(RationCategory.MILKING, "FEMALE", null, null, null, null)).isEqualTo(FeedGroup.MID);
    }

    @Test
    void pregnantDryCowsAndHeifersByDaysToCalving() {
        assertThat(group(RationCategory.DRY, "FEMALE", null, null, DATE.plusDays(90), null)).isEqualTo(FeedGroup.DRY);
        assertThat(group(RationCategory.DRY, "FEMALE", null, null, DATE.plusDays(60), null)).isEqualTo(FeedGroup.STEAM_UP);
        assertThat(group(RationCategory.DRY, "FEMALE", null, null, DATE.plusDays(22), null)).isEqualTo(FeedGroup.STEAM_UP);
        assertThat(group(RationCategory.DRY, "FEMALE", null, null, DATE.plusDays(21), null)).isEqualTo(FeedGroup.CLOSE_UP);
        assertThat(group(RationCategory.DRY, "FEMALE", null, null, DATE.minusDays(3), null)).isEqualTo(FeedGroup.CLOSE_UP);
        assertThat(group(RationCategory.HEIFER, "FEMALE", DATE.minusMonths(22), null, DATE.plusDays(15), null))
                .isEqualTo(FeedGroup.CLOSE_UP);
        assertThat(group(RationCategory.HEIFER, "FEMALE", DATE.minusMonths(18), null, DATE.plusDays(150), null))
                .isEqualTo(FeedGroup.DRY);
        // a milking cow stays in her lactation group even when pregnant
        assertThat(group(RationCategory.MILKING, "FEMALE", null, DATE.minusDays(250), DATE.plusDays(50), null))
                .isEqualTo(FeedGroup.LATE);
    }

    @Test
    void youngStockByAgeAndMaturity() {
        assertThat(group(RationCategory.CALF, "FEMALE", DATE.minusMonths(4), null, null, null)).isEqualTo(FeedGroup.CALF);
        assertThat(group(RationCategory.HEIFER, "FEMALE", DATE.minusMonths(8), null, null, null)).isEqualTo(FeedGroup.CALF);
        assertThat(group(RationCategory.CALF, "FEMALE", DATE.minusMonths(10), null, null, null)).isEqualTo(FeedGroup.HEIFER);
        assertThat(group(RationCategory.HEIFER, "FEMALE", DATE.minusMonths(13), null, null, "200")).isEqualTo(FeedGroup.HEIFER);
        // ready for first service by age or by weight → Dry & mature heifers
        assertThat(group(RationCategory.HEIFER, "FEMALE", DATE.minusMonths(14), null, null, null)).isEqualTo(FeedGroup.DRY);
        assertThat(group(RationCategory.HEIFER, "FEMALE", DATE.minusMonths(12), null, null, "260")).isEqualTo(FeedGroup.DRY);
        // no birth date: category and known weight decide
        assertThat(group(RationCategory.CALF, "FEMALE", null, null, null, null)).isEqualTo(FeedGroup.CALF);
        assertThat(group(RationCategory.HEIFER, "FEMALE", null, null, null, null)).isEqualTo(FeedGroup.HEIFER);
        assertThat(group(RationCategory.HEIFER, "FEMALE", null, null, null, "255")).isEqualTo(FeedGroup.DRY);
    }

    @Test
    void bullsAndMaleCalves() {
        assertThat(group(RationCategory.BULL, "MALE", DATE.minusMonths(30), null, null, null)).isEqualTo(FeedGroup.BULL);
        assertThat(group(RationCategory.CALF, "MALE", DATE.minusMonths(11), null, null, "300")).isEqualTo(FeedGroup.CALF);
    }
}
