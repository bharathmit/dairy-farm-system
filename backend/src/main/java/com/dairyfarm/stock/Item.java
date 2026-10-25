package com.dairyfarm.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dairyfarm.common.FarmScoped;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Something stocked or owned: SEMEN batches (one row per bull batch) and FEED items. */
@Entity
@Table(name = "item")
public class Item extends FarmScoped {

    public enum Type { FEED, SEMEN, MEDICINE, EQUIPMENT, OTHER }

    public enum Unit { KG, L, STRAW, DOSE, NOS }

    /**
     * JSONB details. SEMEN: bullCode, breed, sexed. FEED: nutrients per kg as fed (dmPct, cpPct, tdnPct, caPct, pPct),
     * maxInclusionPct in a concentrate (0 = excluded), excludedReason; BIS quality values fatPct, fibrePct,
     * aiaPct (acid insoluble ash) and aflatoxinPpb, also as fed. {@code excluded} is the older flag, still honoured.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Details(String bullCode, String breed, Boolean sexed,
                          BigDecimal dmPct, BigDecimal cpPct, BigDecimal tdnPct, BigDecimal caPct, BigDecimal pPct,
                          BigDecimal maxInclusionPct, Boolean excluded, String excludedReason,
                          String brand, String model, Integer serviceEveryDays, String serviceTask,
                          Integer reminderDays, LocalDate purchaseDate, BigDecimal purchaseCost,
                          BigDecimal fatPct, BigDecimal fibrePct, BigDecimal aiaPct, BigDecimal aflatoxinPpb) {
        public static final Details EMPTY = new Details(null, null, null);

        public Details(String bullCode, String breed, Boolean sexed,
                       BigDecimal dmPct, BigDecimal cpPct, BigDecimal tdnPct, BigDecimal caPct, BigDecimal pPct,
                       BigDecimal maxInclusionPct, Boolean excluded, String excludedReason) {
            this(bullCode, breed, sexed, dmPct, cpPct, tdnPct, caPct, pPct, maxInclusionPct, excluded, excludedReason,
                    null, null, null, null, null, null, null, null, null, null, null);
        }

        /** FEED details. */
        public static Details feed(BigDecimal dmPct, BigDecimal cpPct, BigDecimal tdnPct, BigDecimal caPct, BigDecimal pPct,
                                   BigDecimal fatPct, BigDecimal fibrePct, BigDecimal aiaPct, BigDecimal aflatoxinPpb,
                                   BigDecimal maxInclusionPct, String excludedReason) {
            return new Details(null, null, null, dmPct, cpPct, tdnPct, caPct, pPct, maxInclusionPct, null, excludedReason,
                    null, null, null, null, null, null, null, fatPct, fibrePct, aiaPct, aflatoxinPpb);
        }

        /** EQUIPMENT details. */
        public static Details equipment(String brand, String model, Integer serviceEveryDays, String serviceTask,
                                        Integer reminderDays, LocalDate purchaseDate, BigDecimal purchaseCost) {
            return new Details(null, null, null, null, null, null, null, null, null, null, null,
                    brand, model, serviceEveryDays, serviceTask, reminderDays, purchaseDate, purchaseCost,
                    null, null, null, null);
        }

        public Details(String bullCode, String breed, Boolean sexed) {
            this(bullCode, breed, sexed, null, null, null, null, null, null, null, null);
        }

        /** Excluded = max inclusion 0 %, or the older excluded flag. */
        public boolean isExcluded() {
            return Boolean.TRUE.equals(excluded) || (maxInclusionPct != null && maxInclusionPct.signum() == 0);
        }
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, updatable = false)
    private Type itemType;

    @Column(nullable = false)
    private String name;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unit unit;

    @Column(name = "cost_per_unit")
    private BigDecimal costPerUnit;

    @Column(name = "supplier_contact_id")
    private Long supplierContactId;

    @Column(name = "reorder_level")
    private BigDecimal reorderLevel;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "batch_no")
    private String batchNo;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Details details = Details.EMPTY;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    protected Item() {
    }

    public Item(Long farmId, Type itemType, Unit unit) {
        super(farmId);
        this.itemType = itemType;
        this.unit = unit;
    }

    public Type getItemType() { return itemType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Unit getUnit() { return unit; }
    public BigDecimal getCostPerUnit() { return costPerUnit; }
    public void setCostPerUnit(BigDecimal costPerUnit) { this.costPerUnit = costPerUnit; }
    public Long getSupplierContactId() { return supplierContactId; }
    public void setSupplierContactId(Long supplierContactId) { this.supplierContactId = supplierContactId; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(BigDecimal reorderLevel) { this.reorderLevel = reorderLevel; }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public Details getDetails() { return details == null ? Details.EMPTY : details; }
    public void setDetails(Details details) { this.details = details; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
