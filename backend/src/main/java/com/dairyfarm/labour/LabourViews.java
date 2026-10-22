package com.dairyfarm.labour;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.dairyfarm.feed.FeedViews.PaymentMethod;
import com.dairyfarm.finance.FinanceViews.MemberRef;
import com.dairyfarm.member.WageType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Shapes for workers, attendance and wages (docs/api-money.md). */
public final class LabourViews {

    private LabourViews() {
    }

    public enum DayStatus { PRESENT, HALF, ABSENT }

    public record Week(LocalDate from, LocalDate to) {
    }

    public record WeekStats(BigDecimal presentDays, BigDecimal hours) {
    }

    public record LastPaid(LocalDate periodTo, BigDecimal amount) {
    }

    public record Worker(MemberRef member, WageType wageType, BigDecimal wageRate, boolean hasLogin, WeekStats week,
                         BigDecimal dueNow, String pendingNote, LastPaid lastPaid) {
    }

    public record WorkerTotals(int workers, BigDecimal dueNow, BigDecimal paidThisMonth) {
    }

    public record Workers(Week week, List<Worker> workers, WorkerTotals totals) {
    }

    public record Day(LocalDate date, DayStatus status, BigDecimal hours) {
    }

    public record AttendanceRow(MemberRef member, WageType wageType, BigDecimal wageRate, List<Day> days,
                                BigDecimal presentDays, BigDecimal hours, BigDecimal amount) {
    }

    public record AttendanceTotal(BigDecimal days, BigDecimal hours, BigDecimal amount) {
    }

    public record AttendanceWeek(Week week, List<AttendanceRow> rows, AttendanceTotal total) {
    }

    public record AttendanceEntry(@NotNull(message = "Pick the worker") Long memberId,
                                  @NotNull(message = "Pick the day") LocalDate date,
                                  DayStatus status,
                                  @DecimalMin(value = "0", message = "Use 0 to 24 hours")
                                  @DecimalMax(value = "24", message = "Use 0 to 24 hours") BigDecimal hours) {
    }

    public record AttendanceRequest(@NotEmpty(message = "Nothing to save") @Size(max = 500) List<@Valid AttendanceEntry> entries) {
    }

    public record WageDue(MemberRef member, LocalDate from, LocalDate to, BigDecimal presentDays, BigDecimal halfDays,
                          BigDecimal absentDays, BigDecimal rate, BigDecimal gross, BigDecimal advances,
                          BigDecimal amountDue, String explain) {
    }

    public record PaymentRequest(@NotNull(message = "Pick the worker") Long memberId,
                                 LocalDate periodFrom, LocalDate periodTo,
                                 @NotNull(message = "Enter the amount paid")
                                 @DecimalMin(value = "0.01", message = "Enter an amount above 0") BigDecimal amount,
                                 PaymentMethod paymentMethod, LocalDate paidOn, Boolean advance,
                                 @Size(max = 2000) String notes) {
    }
}
