package com.dairyfarm.feed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.dairyfarm.contact.ContactResponse.Ref;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes for feed & ration (docs/api-feed.md). Money fields are null for workers. */
public final class FeedViews {

    private FeedViews() {
    }

    public enum Category { CONCENTRATE_INGREDIENT, GREEN_FODDER, DRY_FODDER, SILAGE, MINERAL, MIX }

    public enum Section { GREEN, DRY, CONCENTRATE, OTHER }

    public enum StockStatus { OK, LOW, REORDER, PREPARE, EXCLUDED, UNUSED }

    public enum Target { FARM, GROUP, ANIMAL }

    public enum Session { AM, PM }

    public enum PaymentMethod { CASH, UPI, BANK_TRANSFER, CARD, CHEQUE, OTHER }

    public enum StockMoveType { HARVEST, ADJUSTMENT }

    /** The animal's herd category (animal.category). */
    public enum RationCategory { CALF, HEIFER, MILKING, DRY, BULL }

    /**
     * Feeding group, worked out each day ({@link FeedGroups}); each group has its own ration rule.
     * DRY = dry cows and heifers ready for first service; HEIFER = growing heifers (9 months to first service).
     */
    public enum FeedGroup { FRESH, EARLY, MID, LATE, CLOSE_UP, STEAM_UP, DRY, HEIFER, CALF, BULL }

    public static Section sectionOf(Category c) {
        if (c == null) {
            return Section.OTHER;
        }
        return switch (c) {
            case GREEN_FODDER, SILAGE -> Section.GREEN;
            case DRY_FODDER -> Section.DRY;
            case MIX, CONCENTRATE_INGREDIENT, MINERAL -> Section.CONCENTRATE;
        };
    }

    public record ItemRef(Long id, String name, Category category, String unit) {
    }

    // ---- items -------------------------------------------------------------------------------------------------

    public record FeedItemRequest(
            @NotBlank(message = "Enter a name") @Size(max = 120, message = "Keep the name under 120 characters") String name,
            @NotNull(message = "Pick a category") Category category,
            String unit,
            @DecimalMin(value = "0", message = "Cost can't be negative") @DecimalMax(value = "100000", message = "Cost looks too high") BigDecimal costPerUnit,
            @DecimalMin(value = "0", message = "Use 0 to 100") @DecimalMax(value = "100", message = "Use 0 to 100") BigDecimal dmPct,
            @DecimalMin(value = "0", message = "Use 0 to 100") @DecimalMax(value = "100", message = "Use 0 to 100") BigDecimal cpPct,
            @DecimalMin(value = "0", message = "Use 0 to 100") @DecimalMax(value = "100", message = "Use 0 to 100") BigDecimal tdnPct,
            @DecimalMin(value = "0", message = "Use 0 to 100") @DecimalMax(value = "100", message = "Use 0 to 100") BigDecimal caPct,
            @DecimalMin(value = "0", message = "Use 0 to 100") @DecimalMax(value = "100", message = "Use 0 to 100") BigDecimal pPct,
            @DecimalMin(value = "0", message = "Use 0 to 100") @DecimalMax(value = "100", message = "Use 0 to 100") BigDecimal fatPct,
            @DecimalMin(value = "0", message = "Use 0 to 100") @DecimalMax(value = "100", message = "Use 0 to 100") BigDecimal fibrePct,
            @DecimalMin(value = "0", message = "Use 0 to 100") @DecimalMax(value = "100", message = "Use 0 to 100") BigDecimal aiaPct,
            @DecimalMin(value = "0", message = "Use 0 to 10000 ppb") @DecimalMax(value = "10000", message = "Use 0 to 10000 ppb") BigDecimal aflatoxinPpb,
            @DecimalMin(value = "0", message = "Use 0 to 100") @DecimalMax(value = "100", message = "Use 0 to 100") BigDecimal maxInclusionPct,
            Boolean excluded,   // older clients: true = max inclusion 0 %
            @Size(max = 160) String excludedReason,
            @DecimalMin(value = "0", message = "Can't be negative") BigDecimal reorderLevel,
            @Min(value = 0, message = "Use 0 to 90 days") @Max(value = 90, message = "Use 0 to 90 days") Integer leadTimeDays,
            Long supplierContactId,
            @Size(max = 2000) String notes,
            Boolean active) {
    }

    public record FeedItem(Long id, String name, Category category, String unit, BigDecimal costPerUnit,
                           BigDecimal dmPct, BigDecimal cpPct, BigDecimal tdnPct, BigDecimal caPct, BigDecimal pPct,
                           BigDecimal fatPct, BigDecimal fibrePct, BigDecimal aiaPct, BigDecimal aflatoxinPpb,
                           BigDecimal maxInclusionPct, boolean excluded, String excludedReason, BigDecimal reorderLevel,
                           Integer leadTimeDays, Ref supplier, String notes, boolean active, StockItem stock) {
    }

    public record StockItem(ItemRef item, BigDecimal stock, BigDecimal needPerDay, BigDecimal daysLeft,
                            StockStatus status, int barPct, BigDecimal lastPrice, LocalDate lastPurchaseDate) {
    }

    public record StockRequest(@NotNull(message = "Pick HARVEST or ADJUSTMENT") StockMoveType type,
                               @NotNull(message = "Enter the date") LocalDate date,
                               @NotNull(message = "Enter the quantity") BigDecimal quantity,
                               @Size(max = 500) String notes) {
    }

    public record PurchaseRequest(@NotNull(message = "Pick an item") Long itemId,
                                  @NotNull(message = "Enter the date") LocalDate date,
                                  @NotNull(message = "Enter the quantity")
                                  @DecimalMin(value = "0.001", message = "Enter a quantity above 0") BigDecimal quantity,
                                  @DecimalMin(value = "0", message = "Price can't be negative") BigDecimal costPerUnit,
                                  @DecimalMin(value = "0", message = "Amount can't be negative") BigDecimal totalAmount,
                                  Long supplierContactId, PaymentMethod paymentMethod, Boolean paid,
                                  @Size(max = 500) String notes) {
    }

    public record MoveTarget(Target kind, Long id, String label) {
    }

    public record Move(Long id, LocalDate date, @JsonFormat(pattern = "HH:mm") LocalTime time, String type,
                       BigDecimal quantity, BigDecimal cost, BigDecimal costPerUnit, Ref supplier, PaymentMethod paymentMethod,
                       Boolean paid, Session session, MoveTarget target, String sourceType, String notes) {
    }

    public record PurchaseSaved(Move move, StockItem item) {
    }

    // ---- overview / feeding sheet ------------------------------------------------------------------------------

    public record SessionStatus(boolean logged, BigDecimal kg, BigDecimal plannedKg,
                                @JsonFormat(pattern = "HH:mm") LocalTime time, String by) {
    }

    public record NeedPerDay(BigDecimal green, BigDecimal dry, BigDecimal concentrate) {
    }

    public record Overview(LocalDate date, Map<String, SessionStatus> sessions, NeedPerDay needPerDay,
                           BigDecimal plannedCostPerDay, int reorderCount, List<StockItem> items) {
    }

    /** item is null for a ration part with no feed item picked in the rule (planned only, can't be logged). */
    public record SheetLine(ItemRef item, Section section, BigDecimal plannedKg, BigDecimal actualKg, BigDecimal stock) {
    }

    public record FeedingSheet(LocalDate date, Session session, Target target, FeedGroup feedGroup, String label, int animals,
                               List<SheetLine> lines, BigDecimal plannedKg, BigDecimal actualKg, boolean saved,
                               List<String> warnings) {
    }

    public record SheetEntry(@NotNull(message = "Pick an item") Long itemId,
                             @DecimalMin(value = "0", message = "Use 0 or more kg") BigDecimal kg) {
    }

    public record FeedingRequest(@NotNull(message = "Enter the date") LocalDate date,
                                 @NotNull(message = "Pick the session") Session session,
                                 @JsonFormat(pattern = "HH:mm") LocalTime time,
                                 Target target, Long groupId, FeedGroup feedGroup, Long animalId,
                                 @NotNull @Size(max = 200) List<@Valid SheetEntry> lines,
                                 @Size(max = 500) String notes) {
    }

    // ---- formulas ----------------------------------------------------------------------------------------------

    /** bisGrade: IS 2052:2023 grade the mix is checked against (null = no BIS check). */
    public record Targets(BigDecimal dmPct, BigDecimal cpPct, BigDecimal tdnPct, BigDecimal caPct, BigDecimal pPct,
                          BigDecimal costMin, BigDecimal costMax, BisGrade bisGrade) {
        public static final Targets EMPTY = new Targets(null, null, null, null, null, null, null, null);
    }

    public record FormulaLineRequest(@NotNull(message = "Pick an ingredient") Long itemId,
                                     @NotNull(message = "Enter the kg")
                                     @DecimalMin(value = "0.001", message = "Enter kg above 0") BigDecimal kg,
                                     Integer sortOrder) {
    }

    public record FormulaRequest(@NotBlank(message = "Enter a name") @Size(max = 120) String name,
                                 @NotNull(message = "Pick the mix item") Long mixItemId,
                                 @NotNull(message = "Enter the batch size")
                                 @DecimalMin(value = "1", message = "Batch must be at least 1 kg") BigDecimal batchKg,
                                 @Valid Targets targets,
                                 @NotEmpty(message = "Add at least one ingredient") @Size(max = 40) List<@Valid FormulaLineRequest> lines,
                                 @Size(max = 2000) String notes, Boolean active) {
    }

    public record FormulaLine(ItemRef item, BigDecimal kg, BigDecimal pct, BigDecimal cost, boolean overMax,
                              BigDecimal maxInclusionPct) {
    }

    public record Nutrient(String name, BigDecimal mixPct, BigDecimal targetPct, String status) {
    }

    public record Formula(Long id, String name, ItemRef mixItem, BigDecimal batchKg, BigDecimal totalKg,
                          BigDecimal costPerKg, BigDecimal batchCost, Targets targets, List<FormulaLine> lines,
                          List<Nutrient> nutrients, BisGrade.Check bis, List<String> warnings, String notes, boolean active) {
    }

    public record PreviewLine(ItemRef item, BigDecimal needKg, BigDecimal stock, BigDecimal shortKg) {
    }

    public record BatchPreview(Long formulaId, BigDecimal batchKg, List<PreviewLine> lines, int shortCount,
                               BigDecimal batchCost, BigDecimal costPerKg, BigDecimal mixStockBefore,
                               BigDecimal mixStockAfter, BigDecimal extraDays) {
    }

    public record BatchRequest(@NotNull(message = "Enter the date") LocalDate date,
                               @NotNull(message = "Enter the batch size")
                               @DecimalMin(value = "1", message = "Batch must be at least 1 kg") BigDecimal batchKg,
                               Long preparedByMemberId, Boolean allowShort) {
    }

    public record BatchSaved(BatchPreview preview, StockItem mix) {
    }

    // ---- ration ------------------------------------------------------------------------------------------------

    /** Fresh (as-fed) kg; targetKg = dry matter intake in kg DM (body weight × dmiPct %); waterL = litres/day. */
    public record RationTotals(BigDecimal greenKg, BigDecimal dryKg, BigDecimal concentrateKg, BigDecimal totalKg,
                               BigDecimal targetKg, BigDecimal dmiPct, BigDecimal waterL) {
    }

    public record RationLine(Section section, ItemRef item, BigDecimal kg, String timing) {
    }

    public record IngredientUse(ItemRef item, BigDecimal kgPerDay, BigDecimal limitKg, boolean over) {
    }

    public record NutrientCheck(String name, BigDecimal ration, BigDecimal need, String unit, String status) {
    }

    public record RuleRef(FeedGroup category, LocalDate effectiveFrom) {
    }

    public record RationAnimal(Long id, String tag, String name, String category, String breed) {
    }

    public record AnimalRation(RationAnimal animal, LocalDate date, BigDecimal bodyWeightKg, String weightSource,
                               LocalDate weighedOn, BigDecimal milkYesterday, Integer daysInMilk, String stage,
                               RationTotals totals, String explain, boolean capped, List<RationLine> lines,
                               List<IngredientUse> ingredients, BigDecimal costPerDay, List<NutrientCheck> nutrients,
                               RuleRef rule) {
    }

    public record Totals(BigDecimal greenKg, BigDecimal dryKg, BigDecimal concentrateKg, BigDecimal costPerDay) {
    }

    public record CategoryTotals(FeedGroup category, int animals, Totals totals) {
    }

    public record RationSummary(LocalDate date, Totals farm, List<CategoryTotals> categories) {
    }

    public record NeedRow(Section section, ItemRef item, BigDecimal needPerDay, BigDecimal stock, BigDecimal daysLeft,
                          boolean overLeadTime) {
    }

    public record IngredientRow(ItemRef item, BigDecimal needPerDay, BigDecimal stock, BigDecimal daysLeft,
                                boolean overLeadTime) {
    }

    public record Shortfall(ItemRef item, BigDecimal quantityKg, LocalDate neededBy, BigDecimal expectedPrice) {
    }

    public record FeedNeed(int animals, Totals perDay, BigDecimal costPerMonth, BigDecimal concentrateCostPerDay,
                           BigDecimal concentrateCostPerMonth, List<NeedRow> rows, List<IngredientRow> ingredientRows,
                           List<Shortfall> shortfalls) {
    }

    /** An animal's feeding group on a date (for badges on animal screens). */
    public record AnimalGroup(Long animalId, FeedGroup group) {
    }

    public record RuleVersion(LocalDate effectiveFrom, RationParams params) {
    }

    public record RuleSet(FeedGroup category, RuleVersion current, List<RuleVersion> upcoming,
                          List<RuleVersion> history) {
    }

    public record RuleRequest(@NotNull(message = "Enter the date it takes effect") LocalDate effectiveFrom,
                              @NotNull(message = "Enter the rule") @Valid RationParams params) {
    }
}
