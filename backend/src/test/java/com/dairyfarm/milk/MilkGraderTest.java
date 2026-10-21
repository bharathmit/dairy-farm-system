package com.dairyfarm.milk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.dairyfarm.milk.MilkGrader.Grade;
import com.dairyfarm.milk.MilkGrader.PeakSource;
import com.dairyfarm.milk.MilkGrader.Quality;

class MilkGraderTest {

    private final MilkGradingRules rules = MilkGradingRules.DEFAULTS;
    private final LocalDate calved = LocalDate.of(2026, 7, 30);

    @Test
    void expectedYieldFollowsTheLactationCurve() {
        var day1 = MilkGrader.expect(rules, "HF", calved, calved, null, false);
        assertThat(day1.daysInMilk()).isEqualTo(1);
        assertThat(day1.expectedLitres()).isEqualByComparingTo("12.6"); // 70 % of 18

        var peak = MilkGrader.expect(rules, "HF", calved, calved.plusDays(49), null, false);
        assertThat(peak.expectedLitres()).isEqualByComparingTo("18.0");

        // Ponni: day 84, her own last-lactation peak 18 L → about 16 L
        var ponni = MilkGrader.expect(rules, "HF", calved, calved.plusDays(83), new BigDecimal("18.0"), false);
        assertThat(ponni.daysInMilk()).isEqualTo(84);
        assertThat(ponni.source()).isEqualTo(PeakSource.OWN_LACTATION);
        assertThat(ponni.expectedLitres()).isEqualByComparingTo("16.4");

        var heifer = MilkGrader.expect(rules, "HF", calved, calved.plusDays(49), null, true);
        assertThat(heifer.source()).isEqualTo(PeakSource.FIRST_CALVER);
        assertThat(heifer.expectedLitres()).isEqualByComparingTo("13.5");

        assertThat(MilkGrader.expect(rules, "HF", null, calved, null, false)).isNull();
        assertThat(MilkGrader.expect(rules, "HF", calved, calved.plusDays(320), null, false).pastLactation()).isTrue();
    }

    @Test
    void breedsMatchByNameThenCrossbredThenOther() {
        assertThat(rules.peakFor("jersey").breed()).isEqualTo("Jersey");
        assertThat(rules.peakFor("HF × Jersey").breed()).isEqualTo("Crossbred");
        assertThat(rules.peakFor("Pure Gir").breed()).isEqualTo("Gir");
        assertThat(rules.peakFor("Kangayam").breed()).isEqualTo("Other");
        assertThat(rules.peakFor(null).breed()).isEqualTo("Other");
    }

    @Test
    void gradeFromPercentOfExpectedAndQualityDrops() {
        var exp = MilkGrader.expect(rules, "HF", calved, calved.plusDays(83), new BigDecimal("18.0"), false);
        var b = MilkGrader.grade(rules, new BigDecimal("15.0"), exp, new Quality(new BigDecimal("3.4"), null, null));
        assertThat(b.pctOfExpected()).isEqualByComparingTo("91");
        assertThat(b.grade()).isEqualTo(Grade.B);
        assertThat(b.qualityIssues()).isEmpty();

        var c = MilkGrader.grade(rules, new BigDecimal("15.0"), exp, new Quality(new BigDecimal("3.0"), null, 250));
        assertThat(c.yieldGrade()).isEqualTo(Grade.B);
        assertThat(c.grade()).isEqualTo(Grade.D);
        assertThat(c.qualityIssues()).hasSize(2);

        assertThat(MilkGrader.grade(rules, new BigDecimal("17.0"), exp, Quality.NONE).grade()).isEqualTo(Grade.A);
        assertThat(MilkGrader.grade(rules, new BigDecimal("13.0"), exp, Quality.NONE).grade()).isEqualTo(Grade.C);
        assertThat(MilkGrader.grade(rules, new BigDecimal("12.0"), exp, Quality.NONE).grade()).isEqualTo(Grade.D);
    }
}
