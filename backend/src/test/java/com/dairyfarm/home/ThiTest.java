package com.dairyfarm.home;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.dairyfarm.home.HomeViews.BarnLevel;

class ThiTest {

    private static BigDecimal thi(double t, double rh) {
        return HomeService.thi(BigDecimal.valueOf(t), BigDecimal.valueOf(rh));
    }

    @Test
    void thiFormulaAndLevels() {
        assertThat(thi(25, 50)).isEqualByComparingTo("71.8");
        assertThat(HomeService.level(thi(25, 50))).isEqualTo(BarnLevel.NONE);
        assertThat(HomeService.level(thi(27, 50))).isEqualTo(BarnLevel.MILD);
        assertThat(thi(28, 60)).isEqualByComparingTo("77.0");
        assertThat(HomeService.level(thi(28, 60))).isEqualTo(BarnLevel.MODERATE);
        assertThat(thi(34, 62)).isEqualByComparingTo("85.8");
        assertThat(HomeService.level(thi(34, 62))).isEqualTo(BarnLevel.SEVERE);
        assertThat(HomeService.level(thi(40, 70))).isEqualTo(BarnLevel.EMERGENCY);
    }
}
