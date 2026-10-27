package com.dairyfarm.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import com.dairyfarm.breeding.BreedingViews.AnimalRef;
import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.feed.FeedViews.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/** Request and response shapes for finance (docs/api-money.md). */
public final class FinanceViews {

    private FinanceViews() {
    }

    public enum Direction { INCOME, EXPENSE }

    public enum Category {
        MILK_SALES, ANIMAL_SALES, MANURE_SALES, OTHER_INCOME,
        FEED, LABOUR, VET, BREEDING, SEMEN, EQUIPMENT, UTILITIES, TRANSPORT, ANIMAL_PURCHASE, OTHER_EXPENSE;

        public static final Set<Category> INCOME = Set.of(MILK_SALES, ANIMAL_SALES, MANURE_SALES, OTHER_INCOME);

        public Direction direction() {
            return INCOME.contains(this) ? Direction.INCOME : Direction.EXPENSE;
        }
    }

    public enum Status { PAID, PENDING }

    public enum AllocKind { FARM, GROUP, ANIMAL }

    public record MemberRef(Long id, String name) {
    }

    public record GroupRef(Long id, String name) {
    }

    public record Alloc(AllocKind kind, GroupRef group, AnimalRef animal) {
    }

    public record Source(String type, Long id, String part, String label) {
    }

    public record Entry(Long id, Direction direction, Category category, BigDecimal amount, LocalDate date,
                        @JsonFormat(pattern = "HH:mm") LocalTime time, Status status, PaymentMethod paymentMethod,
                        String counterparty, Ref contact, MemberRef member, LocalDate periodFrom, LocalDate periodTo,
                        boolean advance, Alloc alloc, BigDecimal quantity, String unit, Source source, boolean auto,
                        String notes) {
    }

    public record EntryRequest(
            Direction direction,
            Category category,
            @DecimalMin(value = "0.01", message = "Enter an amount above 0")
            @DecimalMax(value = "100000000", message = "Amount looks too high") BigDecimal amount,
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            Status status, PaymentMethod paymentMethod,
            @Size(max = 160) String counterparty, Long contactId, Long memberId,
            LocalDate periodFrom, LocalDate periodTo,
            AllocKind allocKind, Long groupId, Long animalId,
            @DecimalMin(value = "0", message = "Quantity can't be negative") BigDecimal quantity,
            @Size(max = 6) String unit,
            @Size(max = 2000) String notes) {
    }

    public record CategoryAmount(Category category, BigDecimal amount) {
    }

    public record Totals(BigDecimal income, BigDecimal expense, BigDecimal net) {
    }

    public record Pending(long count, BigDecimal amount) {
    }

    /** Home money panels: one day's feed cost (fed), milk income, other income and other expenses. */
    public record DailyMoney(LocalDate date, BigDecimal feedCost, BigDecimal milkIncome, BigDecimal otherIncome,
                             BigDecimal otherExpense) {
    }

    public record Summary(LocalDate from, LocalDate to, BigDecimal income, BigDecimal expense, BigDecimal net,
                          BigDecimal marginPct, int marginAlertPct, boolean nearAlert, Totals previous,
                          BigDecimal incomeChangePct, BigDecimal expenseChangePct, Pending pending,
                          List<CategoryAmount> incomeByCategory, List<CategoryAmount> expenseByCategory,
                          BigDecimal feedPctOfMilkIncome) {
    }

    public enum PnlScope { FARM, ANIMAL }

    public record PnlAnimal(Long id, String tag, String name, String category, String breed, Integer lactationNumber,
                            Long ageDays) {
    }

    public record RankedAnimal(int rank, PnlAnimal animal, BigDecimal net, String note) {
    }

    public record FarmPnl(BigDecimal income, BigDecimal expense, BigDecimal net, List<CategoryAmount> byCategory) {
    }

    public record Pnl(LocalDate from, LocalDate to, FarmPnl farm, List<RankedAnimal> animals) {
    }

    public enum LineKey { MILK, FEED, HEALTH, BREEDING, PURCHASE, SALE, OTHER }

    public record PnlLine(LineKey key, String label, BigDecimal amount) {
    }

    public record AnimalPnl(PnlAnimal animal, LocalDate from, LocalDate to, long onFarmDays, BigDecimal milkLitres,
                            List<PnlLine> lines, BigDecimal net, BigDecimal perDay, BigDecimal costPerLitre,
                            BigDecimal marginPct, int rank, int of) {
    }
}
