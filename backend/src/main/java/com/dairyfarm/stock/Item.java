package com.dairyfarm.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dairyfarm.common.FarmScoped;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Something stocked or owned. This round uses SEMEN batches (one row per bull batch). */
@Entity
@Table(name = "item")
public class Item extends FarmScoped {

    public enum Type { FEED, SEMEN, MEDICINE, EQUIPMENT, OTHER }

    public enum Unit { KG, L, STRAW, DOSE, NOS }

    /** JSONB details; SEMEN uses bullCode, breed and sexed. */
    public record Details(String bullCode, String breed, Boolean sexed) {
        public static final Details EMPTY = new Details(null, null, null);
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
