package com.dairyfarm.breeding;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.breeding.BreedingRecord.CheckMethod;
import com.dairyfarm.breeding.BreedingRecord.Ease;
import com.dairyfarm.breeding.BreedingRecord.Method;
import com.dairyfarm.breeding.BreedingRecord.Outcome;
import com.dairyfarm.breeding.BreedingRecord.Pregnancy;
import com.dairyfarm.breeding.BreedingSettings.TaperStep;
import com.dairyfarm.common.Sex;
import com.dairyfarm.common.Species;
import com.dairyfarm.contact.ContactResponse.Ref;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes for breeding, semen, calves and family (docs/api-breeding.md). */
public final class BreedingViews {

    private BreedingViews() {
    }

    public record AnimalRef(Long id, String tag, String name) {
    }

    // ---------- status ----------

    public enum Status { OPEN, SERVED, PREGNANT, DRY_PREGNANT, OVERDUE, HEIFER_NOT_SERVED }

    public enum Action { OK, HEAT_DUE, FIRST_SERVICE_DUE, PD_DUE, DRY_OFF_DUE, CALVING_DUE, VET_CHECK, CONSIDER_SELLING }

    public record Suggestion(Action action, String title, List<String> reasons) {
    }

    public record CowStatus(AnimalRef animal, String breed, Category category, int lactationNumber, Status status,
                            LocalDate lastCalvingDate, Integer daysOpen, LocalDate lastHeatDate, LocalDate nextHeatExpected,
                            LocalDate lastServiceDate, Long currentServiceId, LocalDate pdDueDate, LocalDate expectedDueDate,
                            LocalDate dryOffDate, Integer daysToDue, int servicesThisLactation, int failedServices,
                            int heatsMissed, int heatsExpected, BigDecimal spendThisLactation, Suggestion suggestion) {
    }

    public record CalendarItem(LocalDate date, String kind, AnimalRef animal) {
    }

    public record Groups(List<CowStatus> overdue, List<CowStatus> dueIn7, List<CowStatus> open, List<CowStatus> served,
                         List<CowStatus> pregnant, List<CowStatus> dry) {
    }

    public record Overview(LocalDate date, int breedable, int heatWatchCount, List<CalendarItem> calendar, Groups groups) {
    }

    public record HeatWatchItem(CowStatus status, String level, int confidencePct, String reason) {
    }

    // ---------- heats ----------

    public enum Sign { MOUNTING, RESTLESS, MUCUS, SWELLING, CHIN_RESTING, BELLOWING, LOW_APPETITE }

    public enum Intensity { MILD, MODERATE, STRONG, SILENT }

    public record HeatRequest(
            @NotNull(message = "Pick the cow") Long animalId,
            @NotNull(message = "Pick the date") LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            @Size(max = 7) List<Sign> signs,
            @NotNull(message = "Pick the intensity") Intensity intensity,
            @Size(max = 2000) String notes) {
    }

    public record Heat(Long id, AnimalRef animal, LocalDate date, @JsonFormat(pattern = "HH:mm") LocalTime time,
                       List<Sign> signs, Intensity intensity, String notes, Long serviceId) {
    }

    public record HeatSaved(Heat heat, Instant bestServiceAt, LocalDate nextExpected) {
    }

    // ---------- services ----------

    public record ServiceRequest(
            @NotNull(message = "Pick the cow") Long animalId,
            @NotNull(message = "Pick the service date") LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            Long heatId,
            @NotNull(message = "Pick the method") Method method,
            Long semenItemId,
            @Min(value = 1, message = "Use 1 to 5 straws") @Max(value = 5, message = "Use 1 to 5 straws") Integer straws,
            Long sireAnimalId,
            @Size(max = 60) String sireTag,
            LocalDate bullExposureStart,
            LocalDate bullExposureEnd,
            Long technicianContactId,
            @DecimalMin(value = "0", message = "Fee can't be negative") @DecimalMax(value = "1000000", message = "Fee looks too high") BigDecimal technicianFee,
            Long bullOwnerContactId,
            @DecimalMin(value = "0", message = "Fee can't be negative") @DecimalMax(value = "1000000", message = "Fee looks too high") BigDecimal bullFee,
            Boolean feePaid,
            LocalDate expectedDueDate,
            @Size(max = 2000) String notes) {
    }

    public record SemenRef(Long itemId, String bullName, String bullCode, String breed) {
    }

    public record PregCheck(LocalDate date, CheckMethod method, Pregnancy result, Ref checkedBy, BigDecimal cost) {
    }

    public record CalfOut(AnimalRef animal, Sex sex, BigDecimal birthWeightKg) {
    }

    public record Calving(LocalDate date, @JsonFormat(pattern = "HH:mm") LocalTime time, Outcome outcome, Ease ease,
                          List<CalfOut> calves, @JsonFormat(pattern = "HH:mm") LocalTime colostrumGivenAt,
                          BigDecimal colostrumLitres, Instant colostrumDueBy, boolean retainedPlacenta) {
    }

    public record ServiceView(Long id, AnimalRef animal, LocalDate serviceDate, @JsonFormat(pattern = "HH:mm") LocalTime serviceTime,
                          Method method, Long heatId, SemenRef semen, Integer straws, AnimalRef sire, String sireTag,
                          LocalDate bullExposureStart, LocalDate bullExposureEnd, Ref technician, BigDecimal technicianFee,
                          Ref bullOwner, BigDecimal bullFee, BigDecimal semenCost, BigDecimal totalCost, boolean feePaid,
                          LocalDate expectedDueDate, LocalDate pdDueDate, LocalDate dryOffDate, Pregnancy pregnancyStatus,
                          PregCheck pregCheck, Calving calving, int lactationNumber, String notes) {
    }

    public record PregCheckRequest(
            @NotNull(message = "Pick the check date") LocalDate date,
            @NotNull(message = "Pick the method") CheckMethod method,
            @NotNull(message = "Pick the result") Pregnancy result,
            Long checkedByContactId,
            @DecimalMin(value = "0", message = "Cost can't be negative") @DecimalMax(value = "1000000", message = "Cost looks too high") BigDecimal cost,
            LocalDate expectedDueDate) {
    }

    public record CalfIn(
            @NotNull(message = "Pick the calf's sex") Sex sex,
            @DecimalMin(value = "1", message = "Use 1 to 80 kg") @DecimalMax(value = "80", message = "Use 1 to 80 kg") BigDecimal birthWeightKg,
            @Size(max = 40) @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9 ._/-]*$", message = "Use letters, numbers, spaces and . _ / -") String tag,
            @Size(max = 80) String name,
            Boolean createRecord) {
    }

    public record CalvingRequest(
            @NotNull(message = "Pick the dam") Long damId,
            Long serviceId,
            @NotNull(message = "Pick the calving date") LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            @NotNull(message = "Pick the outcome") Outcome outcome,
            Ease ease,
            @Valid @Size(max = 3, message = "Up to 3 calves") List<CalfIn> calves,
            @JsonFormat(pattern = "HH:mm") LocalTime colostrumGivenAt,
            @DecimalMin(value = "0", message = "Use 0 to 10 L") @DecimalMax(value = "10", message = "Use 0 to 10 L") BigDecimal colostrumLitres,
            Boolean retainedPlacenta,
            @Size(max = 2000) String notes) {
    }

    public record CalvingResult(ServiceView record, List<AnimalRef> calves) {
    }

    public record ColostrumRequest(
            @NotNull(message = "Enter the time given") @JsonFormat(pattern = "HH:mm") LocalTime givenAt,
            @DecimalMin(value = "0", message = "Use 0 to 10 L") @DecimalMax(value = "10", message = "Use 0 to 10 L") BigDecimal litres) {
    }

    // ---------- history ----------

    public record HistoryEvent(LocalDate date, String kind, String title, String detail, BigDecimal cost, Long serviceId) {
    }

    public record Current(int lactationNumber, LocalDate calvedOn, List<HistoryEvent> events) {
    }

    public record Lactation(int number, LocalDate calvedOn, LocalDate nextCalvedOn, int services, Integer daysOpen,
                            Integer calvingIntervalDays, BigDecimal spend, boolean conceived) {
    }

    public record Totals(Integer avgDaysOpen, BigDecimal servicesPerConception, int calvings, BigDecimal totalSpend) {
    }

    public record History(CowStatus status, BigDecimal milkPerDay, Current current, List<Lactation> lactations, Totals totals) {
    }

    // ---------- semen ----------

    public record SemenRequest(
            @NotBlank(message = "Enter the bull's name") @Size(max = 120) String bullName,
            @Size(max = 60) String bullCode,
            @Size(max = 60) String breed,
            Long supplierContactId,
            @Size(max = 60) String batchNo,
            LocalDate expiryDate,
            @DecimalMin(value = "0", message = "Cost can't be negative") @DecimalMax(value = "100000", message = "Cost looks too high") BigDecimal costPerStraw,
            Boolean sexed,
            @Min(value = 0, message = "Use 0 to 10000 straws") @Max(value = 10000, message = "Use 0 to 10000 straws") Integer straws,
            @Size(max = 2000) String notes) {
    }

    public record SemenBatch(Long id, String bullName, String bullCode, String breed, Ref supplier, String batchNo,
                             LocalDate expiryDate, BigDecimal costPerStraw, boolean sexed, int straws, boolean low,
                             boolean expiringSoon, LocalDate usedUpOn, String notes) {
    }

    public record SemenTotals(int straws, int low, int expiring30) {
    }

    public record SemenList(SemenTotals totals, List<SemenBatch> batches) {
    }

    public record AdjustRequest(
            @NotNull(message = "Enter the change") @Min(value = -10000, message = "Use −10000 to 10000") @Max(value = 10000, message = "Use −10000 to 10000") Integer quantity,
            @Size(max = 200) String reason) {
    }

    // ---------- calves ----------

    public record Counts(int all, int female, int male, int shown) {
    }

    public record Colostrum(boolean given, Instant dueBy) {
    }

    public record Weaning(boolean ready, int inDays) {
    }

    public record MaleSell(int fromDay, int toDay, int daysLeft) {
    }

    public record Badge(String text, String tone) {
    }

    public record CalfItem(AnimalRef animal, Sex sex, String breed, AnimalRef dam, Integer ageDays, BigDecimal weightKg,
                           Colostrum colostrum, BigDecimal milkLitresPerDay, BigDecimal starterKgPerDay, TaperStep plan,
                           BigDecimal weightTargetKg, Weaning weaning, MaleSell maleSell, List<String> dewormDue,
                           List<Badge> badges) {
    }

    public record CalfList(Counts counts, BigDecimal milkTodayLitres, int needAction, List<CalfItem> calves) {
    }

    public record CalfFeeding(LocalDate date, BigDecimal milkAm, BigDecimal milkPm, BigDecimal starterKg, BigDecimal waterL,
                              BigDecimal colostrumLitres, @JsonFormat(pattern = "HH:mm") LocalTime colostrumAt, String notes) {
    }

    public record FeedingDay(CalfItem calf, LocalDate date, CalfFeeding record, TaperStep plan, List<TaperStep> taper,
                             List<CalfFeeding> recent) {
    }

    public record FeedingRequest(
            @DecimalMin(value = "0", message = "Use 0 to 10 L") @DecimalMax(value = "10", message = "Use 0 to 10 L") BigDecimal milkAm,
            @DecimalMin(value = "0", message = "Use 0 to 10 L") @DecimalMax(value = "10", message = "Use 0 to 10 L") BigDecimal milkPm,
            @DecimalMin(value = "0", message = "Use 0 to 10 kg") @DecimalMax(value = "10", message = "Use 0 to 10 kg") BigDecimal starterKg,
            @DecimalMin(value = "0", message = "Use 0 to 50 L") @DecimalMax(value = "50", message = "Use 0 to 50 L") BigDecimal waterL,
            @DecimalMin(value = "0", message = "Use 0 to 10 L") @DecimalMax(value = "10", message = "Use 0 to 10 L") BigDecimal colostrumLitres,
            @JsonFormat(pattern = "HH:mm") LocalTime colostrumAt,
            @Size(max = 2000) String notes) {
    }

    public enum Session { AM, PM }

    public record SheetCalf(AnimalRef animal, Sex sex, Integer ageDays, BigDecimal planLitres, BigDecimal litres,
                            BigDecimal starterKg, boolean colostrum) {
    }

    public record FeedingSheet(LocalDate date, Session session, List<SheetCalf> calves) {
        /** Only the calves that were in the saved request. */
        public FeedingSheet filterTo(java.util.Set<Long> ids) {
            return new FeedingSheet(date, session, calves.stream().filter(c -> ids.contains(c.animal().id())).toList());
        }
    }

    public record SheetEntry(
            @NotNull Long animalId,
            @DecimalMin(value = "0", message = "Use 0 to 10 L") @DecimalMax(value = "10", message = "Use 0 to 10 L") BigDecimal litres,
            @DecimalMin(value = "0", message = "Use 0 to 10 kg") @DecimalMax(value = "10", message = "Use 0 to 10 kg") BigDecimal starterKg) {
    }

    public record FeedingSheetRequest(
            @NotNull(message = "Pick the date") LocalDate date,
            @NotNull(message = "Pick the session") Session session,
            @NotNull @Valid @Size(max = 500) List<SheetEntry> entries) {
    }

    // ---------- family ----------

    public record FamilyAnimal(Long id, String tag, String name, Species species, String breed, Sex sex,
                               LocalDate dateOfBirth, Category category) {
    }

    public record Node(AnimalRef animal, String label) {
    }

    public record Parents(Node dam, Node sire) {
    }

    public record Grandparents(Node damDam, Node damSire, Node sireDam, Node sireSire) {
    }

    public record Sibling(AnimalRef animal, String kind) {
    }

    public record Offspring(AnimalRef animal, Sex sex, LocalDate birthDate, String sire, String ease,
                            BigDecimal birthWeightKg, String status) {
    }

    public record Family(FamilyAnimal animal, int pedigreeKnownPct, Parents parents, Grandparents grandparents,
                         List<Sibling> siblings, List<Offspring> offspring, Integer avgCalvingIntervalDays) {
    }

    public record MateCheck(String risk, String relation, List<String> sharedAncestors, int knownPct, String text) {
    }

    public record NextTag(String tag) {
    }
}
