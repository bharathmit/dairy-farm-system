package com.dairyfarm.milk;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.milk.MilkGrader.Grade;
import com.dairyfarm.milk.MilkGrader.PeakSource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes for milk records (see docs/api-daily-records.md). */
public final class MilkViews {

    private MilkViews() {
    }

    public enum Session { AM, PM, DAY }

    public enum Product { MILK, MANURE, OTHER }

    public record CowDay(LocalDate date, Long animalId, String tag, String name, BigDecimal am, BigDecimal pm,
                         BigDecimal total, boolean withheld, Integer daysInMilk, BigDecimal expectedLitres,
                         BigDecimal pctOfExpected, Grade grade, List<String> qualityIssues, BigDecimal fatPct,
                         BigDecimal snfPct, BigDecimal proteinPct, BigDecimal density, Integer scc, String notes) {
    }

    public record SessionTotal(BigDecimal litres, int cows, BigDecimal withheldLitres) {
    }

    public record Yesterday(BigDecimal amLitres, BigDecimal pmLitres, BigDecimal totalLitres) {
    }

    public record Change(BigDecimal amPct, BigDecimal pmPct, BigDecimal totalPct) {
    }

    public record DaySummary(LocalDate date, SessionTotal am, SessionTotal pm, BigDecimal totalLitres,
                             Yesterday yesterday, Change change, BigDecimal expectedLitres, int milkingCows,
                             List<CowDay> cows) {
    }

    public record TrendPoint(LocalDate date, BigDecimal amLitres, BigDecimal pmLitres, BigDecimal totalLitres,
                             BigDecimal withheldLitres) {
    }

    public record SheetCow(Long animalId, String tag, String name, Long groupId, BigDecimal litres,
                           BigDecimal lastLitres, BigDecimal usualLitres, boolean inWithdrawal, Instant withdrawalUntil,
                           String stage) {
    }

    public record Sheet(LocalDate date, Session session, boolean saved, BigDecimal sessionLitres, BigDecimal otherSessionLitres,
                        BigDecimal dayLitres, BigDecimal withheldLitres, List<SheetCow> cows, boolean sold) {
    }

    public record AnimalRef(Long id, String tag, String name, String breed, LocalDate lastCalvingDate) {
    }

    public record ExpectationView(int daysInMilk, BigDecimal expectedLitres, BigDecimal peakLitres, int peakDay,
                                  PeakSource source, boolean pastLactation) {
        static ExpectationView of(MilkGrader.Expectation e) {
            return e == null ? null : new ExpectationView(e.daysInMilk(), e.expectedLitres(), e.peakLitres(),
                    e.peakDay(), e.source(), e.pastLactation());
        }
    }

    public record CowDayForm(AnimalRef animal, CowDay day, ExpectationView expectation, BigDecimal lastAm,
                             BigDecimal lastPm, boolean inWithdrawal, Instant withdrawalUntil, boolean amSold,
                             boolean pmSold) {
    }

    public record Sale(Long id, LocalDate date, Session session, Product product, BigDecimal quantity,
                       BigDecimal cowLitres, BigDecimal withheldLitres, BigDecimal soldQty, BigDecimal otherUseQty,
                       BigDecimal rate, BigDecimal incomeAmount, Ref buyer, BigDecimal fatPct, BigDecimal snfPct,
                       Boolean paid, String notes) {
    }

    // ---- requests

    public record SheetEntry(@NotNull(message = "Pick the cow") Long animalId,
                             @DecimalMin(value = "0.0", message = "Litres can't be negative")
                             @DecimalMax(value = "100.0", message = "That's more than 100 L") BigDecimal litres) {
    }

    public record SheetRequest(@NotNull(message = "Pick the date") LocalDate date,
                               @NotNull(message = "Pick morning or evening") Session session,
                               @NotNull @Size(max = 1000, message = "Up to 1000 cows at once") List<@Valid SheetEntry> entries) {
    }

    public record CowDayRequest(
            @DecimalMin(value = "0.0", message = "Litres can't be negative") @DecimalMax(value = "100.0", message = "That's more than 100 L") BigDecimal am,
            @DecimalMin(value = "0.0", message = "Litres can't be negative") @DecimalMax(value = "100.0", message = "That's more than 100 L") BigDecimal pm,
            @DecimalMin(value = "0.0", message = "Use 0 to 15 %") @DecimalMax(value = "15.0", message = "Use 0 to 15 %") BigDecimal fatPct,
            @DecimalMin(value = "0.0", message = "Use 0 to 15 %") @DecimalMax(value = "15.0", message = "Use 0 to 15 %") BigDecimal snfPct,
            @DecimalMin(value = "0.0", message = "Use 0 to 10 %") @DecimalMax(value = "10.0", message = "Use 0 to 10 %") BigDecimal proteinPct,
            @DecimalMin(value = "0.9", message = "Use 0.900 to 1.100") @DecimalMax(value = "1.1", message = "Use 0.900 to 1.100") BigDecimal density,
            @Min(value = 0, message = "SCC can't be negative") @Max(value = 100000, message = "SCC looks too high") Integer scc,
            @Size(max = 2000) String notes,
            List<Session> sessions) {
        /** Sessions to save; null or empty means both (unticked sessions are left as they are). */
        public boolean saves(Session s) {
            return sessions == null || sessions.isEmpty() || sessions.contains(s);
        }
    }

    public record SaleRequest(
            @NotNull(message = "Pick the date") LocalDate date,
            @NotNull(message = "Pick the session") Session session,
            Product product,
            @DecimalMin(value = "0.0", message = "Can't be negative") @DecimalMax(value = "100000", message = "Looks too high") BigDecimal quantity,
            @DecimalMin(value = "0.0", message = "Can't be negative") @DecimalMax(value = "100000", message = "Looks too high") BigDecimal soldQty,
            Long buyerContactId,
            @DecimalMin(value = "0.0", message = "Rate can't be negative") @DecimalMax(value = "1000", message = "Rate looks too high") BigDecimal rate,
            @DecimalMin(value = "0.0", message = "Use 0 to 15 %") @DecimalMax(value = "15.0", message = "Use 0 to 15 %") BigDecimal fatPct,
            @DecimalMin(value = "0.0", message = "Use 0 to 15 %") @DecimalMax(value = "15.0", message = "Use 0 to 15 %") BigDecimal snfPct,
            Boolean paid,
            @Size(max = 2000) String notes) {
    }
}
