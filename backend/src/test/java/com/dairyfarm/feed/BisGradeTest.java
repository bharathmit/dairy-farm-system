package com.dairyfarm.feed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dairyfarm.feed.BisGrade.Check;
import com.dairyfarm.feed.BisGrade.Mix;
import com.dairyfarm.feed.BisGrade.Param;

class BisGradeTest {

    private static Param param(Check c, String name) {
        return c.params().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void comparesOnDryMatterBasis() {
        // As fed: DM 90, CP 18.5 → 20.56 % dry basis: passes Type II (20), fails Type I (22)
        Mix m = new Mix(90.0, 18.5, 3.6, 9.0, 2.0, 0.9, 0.6, 10.0);
        Check two = BisGrade.TYPE_II.check(m);
        assertThat(param(two, "Crude protein").value()).isEqualByComparingTo("20.56");
        assertThat(param(two, "Moisture").value()).isEqualByComparingTo("10.00");
        assertThat(two.status()).isEqualTo("PASS");

        Check one = BisGrade.TYPE_I.check(m);
        assertThat(param(one, "Crude protein").status()).isEqualTo("FAIL");
        assertThat(param(one, "Crude fat").status()).isEqualTo("PASS");     // 4.00 ≥ 4.0
        assertThat(one.status()).isEqualTo("FAIL");
    }

    @Test
    void maxLimitsAndMoisture() {
        // DM 87 → moisture 13 > 11; fibre 11 ÷ 0.87 = 12.64 > 12; AIA 2.7 ÷ 0.87 = 3.10 > 3.0
        Check c = BisGrade.TYPE_II.check(new Mix(87.0, 20.0, 3.0, 11.0, 2.7, 0.8, 0.5, 15.0));
        assertThat(param(c, "Moisture").status()).isEqualTo("FAIL");
        assertThat(param(c, "Crude fibre").status()).isEqualTo("FAIL");
        assertThat(param(c, "Acid insoluble ash").status()).isEqualTo("FAIL");
        assertThat(param(c, "Aflatoxin B1").status()).isEqualTo("PASS");    // 17.24 ppb ≤ 20
        assertThat(param(c, "Aflatoxin B1").unit()).isEqualTo("ppb");
    }

    @Test
    void missingValuesAreUnknown() {
        Check c = BisGrade.TYPE_II.check(new Mix(90.0, 19.0, null, 8.0, 1.5, 1.0, 0.7, 5.0));
        assertThat(param(c, "Crude fat").status()).isEqualTo("UNKNOWN");
        assertThat(c.status()).isEqualTo("INCOMPLETE");

        Check noDm = BisGrade.TYPE_II.check(new Mix(null, 19.0, 4.0, 8.0, 1.5, 1.0, 0.7, 5.0));
        assertThat(noDm.params()).allSatisfy(p -> assertThat(p.status()).isEqualTo("UNKNOWN"));
    }
}
