package com.dairyfarm.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dairyfarm.contact.ContactResponse.Ref;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class HealthViews {

    private HealthViews() {
    }

    public enum HealthType { ILLNESS, TREATMENT, VACCINATION, DEWORMING, INJURY }

    /** The shared fields of a single and a batch health record. */
    public interface Fields {
        LocalDate date();
        HealthType type();
        String symptoms();
        String diagnosis();
        String medicine();
        String dose();
        String route();
        Long vetContactId();
        BigDecimal cost();
        Integer milkWithdrawalDays();
        Integer meatWithdrawalDays();
        LocalDate nextActionDate();
        String nextAction();
        String notes();
    }

    public record HealthRequest(
            @NotNull(message = "Pick the animal") Long animalId,
            @NotNull(message = "Pick the date") LocalDate date,
            @NotNull(message = "Pick the type") HealthType type,
            @Size(max = 2000) String symptoms,
            @Size(max = 200) String diagnosis,
            @Size(max = 120) String medicine,
            @Size(max = 60) String dose,
            @Size(max = 40) String route,
            Long vetContactId,
            @DecimalMin(value = "0.0", message = "Cost can't be negative") @DecimalMax(value = "10000000", message = "Cost looks too high") BigDecimal cost,
            @Min(value = 0, message = "Use 0 to 365 days") @Max(value = 365, message = "Use 0 to 365 days") Integer milkWithdrawalDays,
            @Min(value = 0, message = "Use 0 to 365 days") @Max(value = 365, message = "Use 0 to 365 days") Integer meatWithdrawalDays,
            LocalDate nextActionDate,
            @Size(max = 160) String nextAction,
            @Size(max = 2000) String notes) implements Fields {
    }

    public record HealthBatchRequest(
            @NotEmpty(message = "Pick at least one animal") @Size(max = 500, message = "Up to 500 animals at once") List<@NotNull Long> animalIds,
            @NotNull(message = "Pick the date") LocalDate date,
            @NotNull(message = "Pick the type") HealthType type,
            @Size(max = 2000) String symptoms,
            @Size(max = 200) String diagnosis,
            @Size(max = 120) String medicine,
            @Size(max = 60) String dose,
            @Size(max = 40) String route,
            Long vetContactId,
            @DecimalMin(value = "0.0", message = "Cost can't be negative") @DecimalMax(value = "10000000", message = "Cost looks too high") BigDecimal cost,
            @Min(value = 0, message = "Use 0 to 365 days") @Max(value = 365, message = "Use 0 to 365 days") Integer milkWithdrawalDays,
            @Min(value = 0, message = "Use 0 to 365 days") @Max(value = 365, message = "Use 0 to 365 days") Integer meatWithdrawalDays,
            LocalDate nextActionDate,
            @Size(max = 160) String nextAction,
            @Size(max = 2000) String notes) implements Fields {
    }

    public record FollowUpRequest(@NotNull Boolean done) {
    }

    public record HealthRecord(Long id, Long animalId, String tag, String name, LocalDate date, HealthType type,
                               String symptoms, String diagnosis, String medicine, String dose, String route,
                               Ref vet, BigDecimal cost, Integer milkWithdrawalDays, Integer meatWithdrawalDays,
                               Instant milkWithdrawalUntil, LocalDate meatWithdrawalUntil, LocalDate nextActionDate,
                               String nextAction, boolean nextActionDone, UUID batchId, String notes) {
    }

    public record BatchResult(UUID batchId, int count, List<HealthRecord> records) {
    }

    public record Overview(long inWithdrawal, long openFollowUps, long thisMonth, List<HealthRecord> dueFollowUps,
                           List<HealthRecord> recent) {
    }

    public record Withdrawal(HealthRecord record, Instant endsAt, BigDecimal discardedLitres) {
    }

    public record Withdrawals(List<Withdrawal> active, List<Withdrawal> ended) {
    }
}
