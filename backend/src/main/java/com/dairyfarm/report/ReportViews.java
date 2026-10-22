package com.dairyfarm.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Shapes for reports, the cull advisor and compare cows. */
public final class ReportViews {

    private ReportViews() {
    }

    public enum ReportType { MILK, COWS, BREEDING, FEED, HEALTH, PNL }

    public enum Verdict { KEEP, MONITOR, DRY_OFF, CULL }

    public record CullCounts(int cull, int monitor) {
    }

    public record Summary(LocalDate from, LocalDate to, BigDecimal milkLitres, BigDecimal milkChangePct, BigDecimal netProfit,
                          BigDecimal income, BigDecimal feedCostPerLitre, CullCounts cull) {
    }

    public record Period(LocalDate from, LocalDate to) {
    }

    public record Column(String key, String label) {
    }

    public record Report(String type, String title, Period period, List<Column> columns, List<Map<String, Object>> rows,
                         Map<String, Object> totals) {
    }

    public record CullAnimal(Long id, String tag, String name, String breed, Integer ageYears, int lactationNumber,
                             Integer daysInMilk, String category) {
    }

    public record CullRow(CullAnimal animal, Verdict verdict, int score, int production, int reproduction, int health,
                          List<String> reasons, BigDecimal lifetimeNet, BigDecimal last90Net, LocalDate keepUntil) {
    }

    public record VerdictCounts(int keep, int monitor, int dryOff, int cull) {
    }

    public record CullAdvisor(VerdictCounts counts, List<CullRow> cows) {
    }

    public record HealthTally(int count, BigDecimal cost) {
    }

    public record CowCard(CullAnimal animal, String breed, String status, Integer daysInMilk, String stage, List<BigDecimal> litres7d,
                          BigDecimal avgLitres, BigDecimal trendPct, BigDecimal fat30d, HealthTally health, String activeIssue,
                          String breeding, BigDecimal lifetimeNet) {
    }

    public record Compare(CowCard a, CowCard b, List<LocalDate> days) {
    }
}
