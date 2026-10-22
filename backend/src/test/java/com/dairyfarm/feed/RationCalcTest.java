package com.dairyfarm.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.dairyfarm.feed.FeedViews.Category;
import com.dairyfarm.feed.FeedViews.RationCategory;
import com.dairyfarm.feed.FeedViews.Section;
import com.dairyfarm.feed.RationCalc.AnimalInput;
import com.dairyfarm.feed.RationCalc.ItemInfo;
import com.dairyfarm.feed.RationCalc.Plan;
import com.dairyfarm.feed.RationParams.Limit;
import com.dairyfarm.feed.RationParams.Share;
import com.dairyfarm.feed.RationParams.Stage;

class RationCalcTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 22);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static ItemInfo item(long id, String name, Category c, String cost, String dm, String cp) {
        return new ItemInfo(id, name, c, "KG", cost == null ? null : d(cost), dm == null ? null : d(dm),
                cp == null ? null : d(cp), d("60"), d("0.5"), d("0.3"), false, true);
    }

    private static final Map<Long, ItemInfo> ITEMS = Map.of(
            1L, item(1, "Velimasal", Category.GREEN_FODDER, "2", "20", "4"),
            2L, item(2, "Napier", Category.GREEN_FODDER, "1.5", "20", "2"),
            3L, item(3, "Sorghum green", Category.GREEN_FODDER, "1.5", "25", "2"),
            4L, item(4, "Paddy straw", Category.DRY_FODDER, "4", "90", "3"),
            5L, item(5, "Concentrate mix", Category.MIX, "24", null, null),
            6L, item(6, "Maize", Category.CONCENTRATE_INGREDIENT, "20", "88", "9"),
            7L, item(7, "Gingelly cake", Category.CONCENTRATE_INGREDIENT, "50", "90", "35"));

    private static final Map<Long, Map<Long, BigDecimal>> SHARES = Map.of(5L, Map.of(6L, d("0.93"), 7L, d("0.07")));

    private static AnimalInput cow(RationCategory cat, String kg, String milk, LocalDate calved) {
        return new AnimalInput(1L, "VD-001", "Lakshmi", cat, "HF", d(kg), "ANIMAL", null, milk == null ? null : d(milk), calved);
    }

    private static RationParams rule(List<Share> green, List<Share> dry, List<Limit> limits) {
        return new RationParams(null, null, null, null, null, null, green, dry, limits, null, null, null, null, null);
    }

    @Test
    void milkingCowFollowsBodyWeightAndMilk() {
        Plan p = RationCalc.plan(cow(RationCategory.MILKING, "450", "18.5", DATE.minusDays(83)),
                rule(List.of(), List.of(), List.of()), DATE, ITEMS, SHARES, 5L);
        assertThat(p.targetKg()).isEqualByComparingTo("45");
        assertThat(p.concKg()).isEqualByComparingTo("8.9");
        assertThat(p.greenKg()).isEqualByComparingTo("25.27");
        assertThat(p.dryKg()).isEqualByComparingTo("10.83");
        assertThat(p.totalKg()).isEqualByComparingTo("45");
        assertThat(p.capped()).isFalse();
        assertThat(p.daysInMilk()).isEqualTo(84);
        assertThat(p.stage()).isEqualTo(Stage.EARLY);
        assertThat(p.explain()).contains("450 kg × 10 % = 45 kg", "1.5 + 18.5 L × 0.4");
        // No green/dry items picked yet: one line per part without an item; concentrate goes to the mix
        assertThat(p.lines()).extracting(RationCalc.Line::section).containsExactly(Section.GREEN, Section.DRY, Section.CONCENTRATE);
        assertThat(p.lines().get(0).itemId()).isNull();
        assertThat(p.kgByItem()).containsOnlyKeys(5L);
        // Mix broken into ingredients by the formula: 8.9 × 7 % = 0.62 kg gingelly cake
        assertThat(p.ingredientKg().get(7L)).isEqualByComparingTo("0.62");
        // Cost and nutrients unknown while parts have no items
        assertThat(p.costPerDay()).isNull();
        assertThat(p.nutrients()).extracting(RationCalc.NutrientLine::status).containsOnly("UNKNOWN");
    }

    @Test
    void itemsSplitByShareLimitsByStageAndConcentrateCap() {
        var green = List.of(new Share(1L, d("32")), new Share(2L, d("48")), new Share(3L, d("20")));
        var dry = List.of(new Share(4L, d("100")));
        var limits = List.of(new Limit(7L, d("0.5"), Stage.EARLY));
        Plan early = RationCalc.plan(cow(RationCategory.MILKING, "450", "18.5", DATE.minusDays(83)),
                rule(green, dry, limits), DATE, ITEMS, SHARES, 5L);
        assertThat(early.kgByItem().get(1L)).isEqualByComparingTo("8.09");
        assertThat(early.kgByItem().get(2L)).isEqualByComparingTo("12.13");
        assertThat(early.kgByItem().get(3L)).isEqualByComparingTo("5.05");
        assertThat(early.kgByItem().get(4L)).isEqualByComparingTo("10.83");
        var gingelly = early.ingredients().stream().filter(i -> i.itemId() == 7L).findFirst().orElseThrow();
        assertThat(gingelly.limitKg()).isEqualByComparingTo("0.5");
        assertThat(gingelly.over()).isTrue();
        // cost = 8.09×2 + 12.13×1.5 + 5.05×1.5 + 10.83×4 + 8.9×24
        double expected = 8.09 * 2 + 12.13 * 1.5 + 5.05 * 1.5 + 10.83 * 4 + 8.9 * 24;
        assertThat(early.costPerDay().doubleValue()).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.01));
        assertThat(early.nutrients()).extracting(RationCalc.NutrientLine::status).doesNotContain("UNKNOWN");

        Plan mid = RationCalc.plan(cow(RationCategory.MILKING, "450", "18.5", DATE.minusDays(149)),
                rule(green, dry, limits), DATE, ITEMS, SHARES, 5L);
        assertThat(mid.stage()).isEqualTo(Stage.MID);
        assertThat(mid.ingredients().stream().filter(i -> i.itemId() == 7L).findFirst().orElseThrow().limitKg()).isNull();

        Plan capped = RationCalc.plan(cow(RationCategory.MILKING, "500", "30", DATE.minusDays(40)),
                rule(green, dry, List.of()), DATE, ITEMS, SHARES, 5L);
        assertThat(capped.concKg()).isEqualByComparingTo("10");
        assertThat(capped.capped()).isTrue();
        assertThat(capped.explain()).contains("capped at 10 kg");
    }

    @Test
    void dryCowIgnoresMilkAndHasNoLactationStage() {
        Plan p = RationCalc.plan(cow(RationCategory.DRY, "420", "12", DATE.minusDays(300)),
                rule(List.of(), List.of(), List.of()), DATE, ITEMS, SHARES, 5L);
        assertThat(p.concKg()).isEqualByComparingTo("1.5");
        assertThat(p.targetKg()).isEqualByComparingTo("42");
        assertThat(p.daysInMilk()).isNull();
    }
}
