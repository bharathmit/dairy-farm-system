package com.dairyfarm.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.dairyfarm.feed.FeedViews.Category;
import com.dairyfarm.feed.FeedViews.FeedGroup;
import com.dairyfarm.feed.FeedViews.RationCategory;
import com.dairyfarm.feed.FeedViews.Section;
import com.dairyfarm.feed.RationCalc.AnimalInput;
import com.dairyfarm.feed.RationCalc.ItemInfo;
import com.dairyfarm.feed.RationCalc.Plan;
import com.dairyfarm.feed.RationParams.ConcMode;
import com.dairyfarm.feed.RationParams.Limit;
import com.dairyfarm.feed.RationParams.Share;
import com.dairyfarm.feed.RationParams.Stage;

class RationCalcTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 22);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static ItemInfo item(long id, String name, Category c, String cost, String dm, String cp, boolean active) {
        return new ItemInfo(id, name, c, "KG", cost == null ? null : d(cost), dm == null ? null : d(dm),
                cp == null ? null : d(cp), d("60"), d("0.5"), d("0.3"), false, active);
    }

    private static ItemInfo item(long id, String name, Category c, String cost, String dm, String cp) {
        return item(id, name, c, cost, dm, cp, true);
    }

    private static final Map<Long, ItemInfo> ITEMS = Map.of(
            1L, item(1, "Corn silage", Category.SILAGE, "3", "30", "8"),
            2L, item(2, "Hydroponic fodder", Category.GREEN_FODDER, "2", "10", "14"),
            3L, item(3, "Azolla", Category.GREEN_FODDER, "1", "5", "25"),
            4L, item(4, "Paddy straw", Category.DRY_FODDER, "4", "90", "3"),
            5L, item(5, "Concentrate mix", Category.MIX, "24", null, null),
            6L, item(6, "Maize", Category.CONCENTRATE_INGREDIENT, "20", "88", "9"),
            7L, item(7, "Gingelly cake", Category.CONCENTRATE_INGREDIENT, "50", "90", "35"));

    /** Mix DM from its formula: 0.93 × 88 + 0.07 × 90 = 88.14 %. */
    private static final Map<Long, Map<Long, BigDecimal>> SHARES = Map.of(5L, Map.of(6L, d("0.93"), 7L, d("0.07")));

    private static AnimalInput cow(RationCategory cat, String kg, String milk, LocalDate calved, LocalDate born) {
        // a group whose defaults match the old category defaults
        FeedGroup g = switch (cat) {
            case MILKING -> FeedGroup.EARLY;
            case DRY -> FeedGroup.DRY;
            case HEIFER -> FeedGroup.HEIFER;
            case CALF -> FeedGroup.CALF;
            case BULL -> FeedGroup.BULL;
        };
        return new AnimalInput(1L, "VD-001", "Lakshmi", cat, "HF", d(kg), "ANIMAL", null, milk == null ? null : d(milk),
                calved, born, g);
    }

    private static RationParams rule(List<Share> green, List<Share> dry, List<Limit> limits) {
        return new RationParams(null, null, null, null, null, null, null, null, green, dry, limits, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private static final List<Share> GREEN = List.of(new Share(1L, d("100")), new Share(2L, d("0"), d("7")),
            new Share(3L, d("0"), d("1")));
    private static final List<Share> DRY = List.of(new Share(4L, d("100")));

    @Test
    void earlyLactationCowFollowsDmiAndMilk() {
        // 450 kg × 3.75 % = 16.875 kg DM; concentrate 1.5 + 18 × 0.4 = 8.7 kg (7.67 kg DM);
        // forage DM 9.21 split 45 : 15 → green 6.91 kg DM (÷ 30 % = 23.02 kg), dry 2.30 kg DM (÷ 90 % = 2.56 kg)
        Plan p = RationCalc.plan(cow(RationCategory.MILKING, "450", "18", DATE.minusDays(83), null),
                rule(List.of(), List.of(), List.of()), DATE, ITEMS, SHARES, 5L);
        assertThat(p.stage()).isEqualTo(Stage.EARLY);
        assertThat(p.daysInMilk()).isEqualTo(84);
        assertThat(p.dmiPct()).isEqualByComparingTo("3.75");
        assertThat(p.targetKg()).isEqualByComparingTo("16.88");
        assertThat(p.concKg()).isEqualByComparingTo("8.7");
        assertThat(p.greenKg()).isEqualByComparingTo("23.02");
        assertThat(p.dryKg()).isEqualByComparingTo("2.56");
        assertThat(p.totalKg()).isEqualByComparingTo("34.28");
        assertThat(p.waterL()).isEqualByComparingTo("102");
        assertThat(p.capped()).isFalse();
        assertThat(p.explain()).contains("450 kg × 3.75 % (early lactation) = 16.9 kg DM", "1.5 + 18 L × 0.4 = 8.7 kg");
        // No fodder items picked yet: one line per part without an item; concentrate goes to the mix
        assertThat(p.lines()).extracting(RationCalc.Line::section).containsExactly(Section.GREEN, Section.DRY, Section.CONCENTRATE);
        assertThat(p.lines().get(0).itemId()).isNull();
        assertThat(p.kgByItem()).containsOnlyKeys(5L);
        assertThat(p.ingredientKg().get(7L)).isEqualByComparingTo("0.61");
        assertThat(p.costPerDay()).isNull();
        // Nutrient need uses the same DMI
        assertThat(p.nutrients().get(0).need()).isEqualByComparingTo("16.88");
    }

    @Test
    void fixedGreenItemsComeOffSilageAndAreOptional() {
        var limits = List.of(new Limit(3L, d("1.5"), Stage.ANY), new Limit(7L, d("0.5"), Stage.EARLY));
        Plan p = RationCalc.plan(cow(RationCategory.MILKING, "450", "18", DATE.minusDays(83), null),
                rule(GREEN, DRY, limits), DATE, ITEMS, SHARES, 5L);
        // green DM 6.905 − hydroponic 0.7 − azolla 0.05 = 6.155 kg DM ÷ 30 % = 20.52 kg silage
        assertThat(p.kgByItem().get(1L)).isEqualByComparingTo("20.52");
        assertThat(p.kgByItem().get(2L)).isEqualByComparingTo("7");
        assertThat(p.kgByItem().get(3L)).isEqualByComparingTo("1");
        assertThat(p.kgByItem().get(4L)).isEqualByComparingTo("2.56");
        assertThat(p.greenKg()).isEqualByComparingTo("28.52");
        var azolla = p.ingredients().stream().filter(i -> i.itemId() == 3L).findFirst().orElseThrow();
        assertThat(azolla.limitKg()).isEqualByComparingTo("1.5");
        assertThat(azolla.over()).isFalse();
        var gingelly = p.ingredients().stream().filter(i -> i.itemId() == 7L).findFirst().orElseThrow();
        assertThat(gingelly.over()).isTrue();
        double cost = 20.52 * 3 + 7 * 2 + 1 * 1 + 2.56 * 4 + 8.7 * 24;
        assertThat(p.costPerDay().doubleValue()).isCloseTo(cost, within(0.01));
        assertThat(p.nutrients()).extracting(RationCalc.NutrientLine::status).doesNotContain("UNKNOWN");

        // Hydroponic setup switched off: silage fills its DM
        Map<Long, ItemInfo> off = new HashMap<>(ITEMS);
        off.put(2L, item(2, "Hydroponic fodder", Category.GREEN_FODDER, "2", "10", "14", false));
        Plan without = RationCalc.plan(cow(RationCategory.MILKING, "450", "18", DATE.minusDays(83), null),
                rule(GREEN, DRY, List.of()), DATE, off, SHARES, 5L);
        assertThat(without.kgByItem()).doesNotContainKey(2L);
        assertThat(without.kgByItem().get(1L)).isEqualByComparingTo("22.85");
    }

    @Test
    void stagesAndConcentrateCap() {
        Plan mid = RationCalc.plan(cow(RationCategory.MILKING, "450", "18", DATE.minusDays(149), null),
                rule(GREEN, DRY, List.of()), DATE, ITEMS, SHARES, 5L);
        assertThat(mid.stage()).isEqualTo(Stage.MID);
        assertThat(mid.dmiPct()).isEqualByComparingTo("3.25");
        Plan late = RationCalc.plan(cow(RationCategory.MILKING, "450", "8", DATE.minusDays(250), null),
                rule(GREEN, DRY, List.of()), DATE, ITEMS, SHARES, 5L);
        assertThat(late.dmiPct()).isEqualByComparingTo("2.75");

        Plan capped = RationCalc.plan(cow(RationCategory.MILKING, "500", "30", DATE.minusDays(40), null),
                rule(GREEN, DRY, List.of()), DATE, ITEMS, SHARES, 5L);
        assertThat(capped.concKg()).isEqualByComparingTo("10");
        assertThat(capped.capped()).isTrue();
        assertThat(capped.explain()).contains("capped at 10 kg");
    }

    @Test
    void dryCowUsesDmiShareForConcentrate() {
        // 420 × 2 % = 8.4 kg DM; concentrate 30 % = 2.52 kg DM ÷ 88.14 % = 2.86 kg; forage 5.88 DM → 45 : 25
        Plan p = RationCalc.plan(cow(RationCategory.DRY, "420", "12", DATE.minusDays(300), null),
                rule(GREEN, DRY, List.of()), DATE, ITEMS, SHARES, 5L);
        assertThat(p.daysInMilk()).isNull();
        assertThat(p.targetKg()).isEqualByComparingTo("8.4");
        assertThat(p.concKg()).isEqualByComparingTo("2.86");
        assertThat(p.kgByItem().get(4L)).isEqualByComparingTo("2.33");
        assertThat(p.waterL()).isEqualByComparingTo("30");
        assertThat(p.explain()).contains("(dry)", "30 % ÷ 88.1 % DM");
    }

    @Test
    void heiferDmiByAgeAndMilkModeOverride() {
        Plan p = RationCalc.plan(cow(RationCategory.HEIFER, "250", null, null, DATE.minusMonths(8)),
                rule(GREEN, DRY, List.of()), DATE, ITEMS, SHARES, 5L);
        assertThat(p.ageMonths()).isEqualTo(8);
        assertThat(p.dmiPct()).isEqualByComparingTo("2.75");
        assertThat(p.explain()).contains("8 months old");
        assertThat(p.concKg()).isEqualByComparingTo("2");
        assertThat(p.capped()).isTrue();

        Plan noDob = RationCalc.plan(cow(RationCategory.HEIFER, "250", null, null, null),
                rule(GREEN, DRY, List.of()), DATE, ITEMS, SHARES, 5L);
        assertThat(noDob.dmiPct()).isEqualByComparingTo("2.5");

        // Owner switches a category to the milk formula
        var milkRule = new RationParams(ConcMode.MILK, null, null, null, null, null, null, null, GREEN, DRY, List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        Plan byMilk = RationCalc.plan(cow(RationCategory.HEIFER, "250", null, null, DATE.minusMonths(8)), milkRule, DATE,
                ITEMS, SHARES, 5L);
        assertThat(byMilk.concKg()).isEqualByComparingTo("1");
    }
}
