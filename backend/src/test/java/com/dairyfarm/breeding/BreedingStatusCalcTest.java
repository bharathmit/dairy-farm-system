package com.dairyfarm.breeding;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.breeding.BreedingRecord.Pregnancy;
import com.dairyfarm.breeding.BreedingStatusCalc.Cow;
import com.dairyfarm.breeding.BreedingStatusCalc.Svc;
import com.dairyfarm.breeding.BreedingViews.Action;
import com.dairyfarm.breeding.BreedingViews.CowStatus;
import com.dairyfarm.breeding.BreedingViews.Status;

class BreedingStatusCalcTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final BreedingSettings S = BreedingSettings.DEFAULTS;

    private static Cow cow(int daysSinceCalving, int lactation, Category category) {
        return new Cow(1L, "VD-011", "Malar", "HF", category, TODAY.minusYears(5),
                daysSinceCalving < 0 ? null : TODAY.minusDays(daysSinceCalving), lactation, null, new BigDecimal("12.5"));
    }

    private static CowStatus run(Cow cow, List<LocalDate> heats, List<Svc> svcs) {
        return BreedingStatusCalc.compute(S, cow, heats, svcs, TODAY, true).status();
    }

    private static Svc svc(long id, int daysAgo, Pregnancy p, int lactation) {
        LocalDate d = TODAY.minusDays(daysAgo);
        return new Svc(id, d, p, null, d.plusDays(283), lactation, new BigDecimal("750"));
    }

    @Test
    void openCowWithNoHeatsSeenNeedsAVetCheck() {
        CowStatus s = run(cow(72, 2, Category.MILKING), List.of(), List.of());
        assertThat(s.status()).isEqualTo(Status.OPEN);
        assertThat(s.daysOpen()).isEqualTo(72);
        assertThat(s.heatsMissed()).isEqualTo(2);           // expected around day 45 and 66
        assertThat(s.suggestion().action()).isEqualTo(Action.VET_CHECK);
        assertThat(s.suggestion().reasons()).anyMatch(r -> r.contains("Still giving 12.5 L/day"));
    }

    @Test
    void returnToHeatAndANegativeCheckCountAsFailedServices() {
        var svcs = List.of(svc(1, 130, Pregnancy.UNKNOWN, 2), svc(2, 109, Pregnancy.NOT_PREGNANT, 2));
        CowStatus s = run(cow(160, 2, Category.MILKING), List.of(TODAY.minusDays(109)), svcs);
        assertThat(s.status()).isEqualTo(Status.OPEN);
        assertThat(s.failedServices()).isEqualTo(2);
        assertThat(s.servicesThisLactation()).isEqualTo(2);
        assertThat(s.spendThisLactation()).isEqualByComparingTo("1500");
        assertThat(s.suggestion().action()).isEqualTo(Action.CONSIDER_SELLING);
        assertThat(s.suggestion().reasons()).contains("2 failed services");
    }

    @Test
    void threeFailedServicesMeanConsiderSellingEvenEarly() {
        var svcs = List.of(svc(1, 70, Pregnancy.NOT_PREGNANT, 1), svc(2, 49, Pregnancy.NOT_PREGNANT, 1),
                svc(3, 28, Pregnancy.NOT_PREGNANT, 1));
        CowStatus s = run(cow(100, 1, Category.MILKING), List.of(), svcs);
        assertThat(s.failedServices()).isEqualTo(3);
        assertThat(s.suggestion().action()).isEqualTo(Action.CONSIDER_SELLING);
    }

    @Test
    void heatSeenThreeWeeksAgoMeansHeatDue() {
        CowStatus s = run(cow(60, 1, Category.MILKING), List.of(TODAY.minusDays(20)), List.of());
        assertThat(s.nextHeatExpected()).isEqualTo(TODAY.plusDays(1));
        assertThat(s.suggestion().action()).isEqualTo(Action.HEAT_DUE);
    }

    @Test
    void servedCowAwaitsPregnancyCheck() {
        CowStatus s = run(cow(120, 1, Category.MILKING), List.of(), List.of(svc(1, 50, Pregnancy.UNKNOWN, 1)));
        assertThat(s.status()).isEqualTo(Status.SERVED);
        assertThat(s.pdDueDate()).isEqualTo(TODAY.minusDays(5));
        assertThat(s.daysOpen()).isEqualTo(70);
        assertThat(s.suggestion().action()).isEqualTo(Action.PD_DUE);
    }

    @Test
    void pregnantCowsGetDryOffAndCalvingReminders() {
        Svc dryOffSoon = new Svc(1L, TODAY.minusDays(222), Pregnancy.PREGNANT, null, TODAY.plusDays(61), 1, null);
        CowStatus a = run(cow(320, 1, Category.MILKING), List.of(), List.of(dryOffSoon));
        assertThat(a.status()).isEqualTo(Status.PREGNANT);
        assertThat(a.dryOffDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(a.suggestion().action()).isEqualTo(Action.DRY_OFF_DUE);

        Svc dueSoon = new Svc(1L, TODAY.minusDays(278), Pregnancy.PREGNANT, null, TODAY.plusDays(5), 1, null);
        CowStatus b = run(cow(380, 1, Category.DRY), List.of(), List.of(dueSoon));
        assertThat(b.status()).isEqualTo(Status.DRY_PREGNANT);
        assertThat(b.suggestion().action()).isEqualTo(Action.CALVING_DUE);

        Svc overdue = new Svc(1L, TODAY.minusDays(290), Pregnancy.PREGNANT, null, TODAY.minusDays(4), 1, null);
        assertThat(run(cow(400, 1, Category.DRY), List.of(), List.of(overdue)).status()).isEqualTo(Status.OVERDUE);
    }

    @Test
    void heiferOfServiceAgeIsDueForFirstService() {
        Cow heifer = new Cow(2L, "VD-015", "Ganga", "HF", Category.HEIFER, TODAY.minusMonths(15), null, 0,
                new BigDecimal("280"), null);
        CowStatus s = run(heifer, List.of(), List.of());
        assertThat(s.status()).isEqualTo(Status.HEIFER_NOT_SERVED);
        assertThat(s.daysOpen()).isNull();
        assertThat(s.suggestion().action()).isEqualTo(Action.FIRST_SERVICE_DUE);
    }
}
