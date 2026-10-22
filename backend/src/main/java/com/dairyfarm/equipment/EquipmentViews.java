package com.dairyfarm.equipment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.feed.FeedViews.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class EquipmentViews {

    private EquipmentViews() {
    }

    public enum ServiceStatus { OVERDUE, DUE_SOON, OK }

    public enum WorkDone { ROUTINE, LINER_CHANGE, REPAIR, PARTS, OTHER }

    public record Equipment(Long id, String name, String brand, String model, Integer serviceEveryDays,
                            String serviceTask, Integer reminderDays, LocalDate lastService, LocalDate nextService,
                            ServiceStatus status, Long daysTo, LocalDate purchaseDate, BigDecimal purchaseCost,
                            String notes) {
    }

    public record Counts(int items, int overdue, int dueSoon) {
    }

    public record EquipmentList(Counts counts, List<Equipment> items) {
    }

    public record EquipmentRequest(
            @NotBlank(message = "Enter a name") @Size(max = 120, message = "Keep the name under 120 characters") String name,
            @Size(max = 60) String brand, @Size(max = 60) String model,
            @Min(value = 1, message = "Use 1 to 730 days") @Max(value = 730, message = "Use 1 to 730 days") Integer serviceEveryDays,
            @Size(max = 120) String serviceTask,
            @Min(value = 0, message = "Use 0 to 60 days") @Max(value = 60, message = "Use 0 to 60 days") Integer reminderDays,
            LocalDate purchaseDate,
            @DecimalMin(value = "0", message = "Cost can't be negative") BigDecimal purchaseCost,
            @Size(max = 2000) String notes) {
    }

    public record ServiceRequest(@NotNull(message = "Enter the service date") LocalDate date,
                                 WorkDone workDone,
                                 @DecimalMin(value = "0", message = "Cost can't be negative") BigDecimal cost,
                                 PaymentMethod paymentMethod, Boolean paid, Long mechanicContactId,
                                 @Size(max = 120) String mechanicName, @Size(max = 2000) String notes,
                                 LocalDate nextServiceDate) {
    }

    public record Service(Long id, LocalDate date, WorkDone workDone, BigDecimal cost, PaymentMethod paymentMethod,
                          Boolean paid, Ref mechanic, String mechanicName, String notes, LocalDate nextServiceDate) {
    }
}
