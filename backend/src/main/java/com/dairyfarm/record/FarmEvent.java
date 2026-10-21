package com.dairyfarm.record;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dairyfarm.common.FarmScoped;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Something observed on a date: a weight/BCS measurement or a health record (more types in later modules). */
@Entity
@Table(name = "farm_event")
public class FarmEvent extends FarmScoped {

    public enum EventType { MEASUREMENT, HEALTH, HEAT, CALF_FEEDING, BARN_READING, ATTENDANCE }

    /** Free-form parts of the record, kept as JSONB. Unused values stay null. */
    public record Details(String symptoms, String diagnosis, String medicine, String dose, String route,
                          Integer milkWithdrawalDays, Integer meatWithdrawalDays, BigDecimal heartGirthCm) {
        public static final Details EMPTY = new Details(null, null, null, null, null, null, null, null);
    }

    @Column(name = "animal_id")
    private Long animalId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private EventType eventType;

    @Column(name = "sub_type")
    private String subType;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_time")
    private LocalTime eventTime;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    private BigDecimal bcs;
    private BigDecimal cost;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "next_action_date")
    private LocalDate nextActionDate;

    @Column(name = "next_action")
    private String nextAction;

    @Column(name = "next_action_done", nullable = false)
    private boolean nextActionDone;

    @Column(name = "milk_withdrawal_until")
    private Instant milkWithdrawalUntil;

    @Column(name = "meat_withdrawal_until")
    private LocalDate meatWithdrawalUntil;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Details details = Details.EMPTY;

    private String notes;

    protected FarmEvent() {
    }

    public FarmEvent(Long farmId, EventType eventType) {
        super(farmId);
        this.eventType = eventType;
    }

    public Long getAnimalId() { return animalId; }
    public void setAnimalId(Long animalId) { this.animalId = animalId; }
    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public EventType getEventType() { return eventType; }
    public String getSubType() { return subType; }
    public void setSubType(String subType) { this.subType = subType; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public LocalTime getEventTime() { return eventTime; }
    public void setEventTime(LocalTime eventTime) { this.eventTime = eventTime; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
    public BigDecimal getBcs() { return bcs; }
    public void setBcs(BigDecimal bcs) { this.bcs = bcs; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public LocalDate getNextActionDate() { return nextActionDate; }
    public void setNextActionDate(LocalDate nextActionDate) { this.nextActionDate = nextActionDate; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public boolean isNextActionDone() { return nextActionDone; }
    public void setNextActionDone(boolean nextActionDone) { this.nextActionDone = nextActionDone; }
    public Instant getMilkWithdrawalUntil() { return milkWithdrawalUntil; }
    public void setMilkWithdrawalUntil(Instant milkWithdrawalUntil) { this.milkWithdrawalUntil = milkWithdrawalUntil; }
    public LocalDate getMeatWithdrawalUntil() { return meatWithdrawalUntil; }
    public void setMeatWithdrawalUntil(LocalDate meatWithdrawalUntil) { this.meatWithdrawalUntil = meatWithdrawalUntil; }
    public Details getDetails() { return details == null ? Details.EMPTY : details; }
    public void setDetails(Details details) { this.details = details; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
