package com.dairyfarm.milk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.dairyfarm.milk.MilkGradingRules.BreedPeak;
import com.dairyfarm.milk.MilkGradingRules.Check;

/** Expected yield by lactation day and the A–D grade. Pure calculation, no database. */
public final class MilkGrader {

    public enum Grade { A, B, C, D }

    /** Where the expected value came from. */
    public enum PeakSource { OWN_LACTATION, BREED, FIRST_CALVER }

    public record Expectation(int daysInMilk, BigDecimal expectedLitres, BigDecimal peakLitres, int peakDay,
                              PeakSource source, boolean pastLactation) {
    }

    public record Quality(BigDecimal fatPct, BigDecimal snfPct, Integer scc) {
        public static final Quality NONE = new Quality(null, null, null);
    }

    public record Result(Grade grade, BigDecimal pctOfExpected, Grade yieldGrade, List<String> qualityIssues) {
    }

    private MilkGrader() {
    }

    /**
     * Expected litres for the day, or null when the calving date is unknown or after the date.
     *
     * @param ownPeak best day total in the cow's previous lactation, or null
     * @param firstCalver true when this is her first lactation
     */
    public static Expectation expect(MilkGradingRules rules, String breed, LocalDate lastCalving, LocalDate date,
                                     BigDecimal ownPeak, boolean firstCalver) {
        if (lastCalving == null || date.isBefore(lastCalving)) {
            return null;
        }
        var r = rules.resolved();
        int dim = (int) ChronoUnit.DAYS.between(lastCalving, date) + 1;
        BreedPeak bp = r.peakFor(breed);
        int peakDay = bp.peakDay();
        double peak;
        PeakSource source;
        if (r.basis() == MilkGradingRules.Basis.OWN_LACTATION && ownPeak != null && ownPeak.signum() > 0) {
            peak = ownPeak.doubleValue();
            source = PeakSource.OWN_LACTATION;
        } else if (firstCalver) {
            peak = bp.peakLitres().doubleValue() * r.firstCalverPct() / 100.0;
            source = PeakSource.FIRST_CALVER;
        } else {
            peak = bp.peakLitres().doubleValue();
            source = PeakSource.BREED;
        }
        double start = r.startPct() / 100.0;
        double expected;
        if (dim <= peakDay) {
            expected = peak * (start + (1 - start) * (dim - 1) / Math.max(1, peakDay - 1));
        } else {
            double months = (dim - peakDay) / 30.4;
            expected = peak * Math.pow(1 - r.fallPctPerMonth().doubleValue() / 100.0, months);
        }
        return new Expectation(dim, litres(expected), litres(peak), peakDay, source, dim > r.lactationDays());
    }

    /** Grade for a full day (both sessions logged). Null when there is nothing to compare with. */
    public static Result grade(MilkGradingRules rules, BigDecimal actual, Expectation exp, Quality q) {
        if (actual == null || exp == null || exp.pastLactation() || exp.expectedLitres().signum() <= 0) {
            return null;
        }
        var r = rules.resolved();
        BigDecimal pct = actual.multiply(BigDecimal.valueOf(100))
                .divide(exp.expectedLitres(), 0, RoundingMode.HALF_UP);
        int p = pct.intValue();
        Grade yieldGrade = p >= r.bandA() ? Grade.A : p >= r.bandB() ? Grade.B : p >= r.bandC() ? Grade.C : Grade.D;
        List<String> issues = new ArrayList<>();
        if (q != null) {
            if (below(r.minFatPct(), q.fatPct())) {
                issues.add("Fat " + q.fatPct().stripTrailingZeros().toPlainString() + " % is below "
                        + r.minFatPct().limit().stripTrailingZeros().toPlainString() + " %");
            }
            if (below(r.minSnfPct(), q.snfPct())) {
                issues.add("SNF " + q.snfPct().stripTrailingZeros().toPlainString() + " % is below "
                        + r.minSnfPct().limit().stripTrailingZeros().toPlainString() + " %");
            }
            if (q.scc() != null && on(r.maxSccK()) && q.scc() > r.maxSccK().limit().doubleValue()) {
                issues.add("SCC " + q.scc() + "k is above " + r.maxSccK().limit().stripTrailingZeros().toPlainString() + "k");
            }
        }
        Grade g = Grade.values()[Math.min(Grade.D.ordinal(), yieldGrade.ordinal() + issues.size())];
        return new Result(g, pct, yieldGrade, issues);
    }

    private static boolean on(Check c) {
        return c != null && Boolean.TRUE.equals(c.enabled());
    }

    private static boolean below(Check c, BigDecimal value) {
        return value != null && on(c) && value.compareTo(c.limit()) < 0;
    }

    static BigDecimal litres(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }
}
