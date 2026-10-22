package com.dairyfarm.breeding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dairyfarm.common.FarmScoped;
import com.dairyfarm.common.Sex;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** One service → pregnancy check → calving cycle for a dam. */
@Entity
@Table(name = "breeding_record")
public class BreedingRecord extends FarmScoped {

    public enum Method { NATURAL, AI, ET, UNKNOWN }

    public enum Pregnancy { UNKNOWN, PREGNANT, NOT_PREGNANT }

    public enum CheckMethod { ULTRASOUND, PALPATION, BLOOD }

    public enum Outcome { LIVE, STILLBORN, ABORTED }

    public enum Ease { EASY, ASSISTED, HARD, VET }

    /** A calf born from this record (animalId null when no calf record was made). */
    public record Calf(Long animalId, Sex sex, BigDecimal birthWeightKg) {
    }

    @Column(name = "dam_id", nullable = false)
    private Long damId;

    @Column(name = "lactation_number", nullable = false)
    private int lactationNumber;

    @Column(name = "heat_event_id")
    private Long heatEventId;

    @Column(name = "service_date")
    private LocalDate serviceDate;

    @Column(name = "service_time")
    private LocalTime serviceTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Method method;

    @Column(name = "semen_item_id")
    private Long semenItemId;

    private Integer straws;

    @Column(name = "sire_id")
    private Long sireId;

    @Column(name = "sire_tag")
    private String sireTag;

    @Column(name = "bull_exposure_start")
    private LocalDate bullExposureStart;

    @Column(name = "bull_exposure_end")
    private LocalDate bullExposureEnd;

    @Column(name = "technician_contact_id")
    private Long technicianContactId;

    @Column(name = "technician_fee")
    private BigDecimal technicianFee;

    @Column(name = "bull_owner_contact_id")
    private Long bullOwnerContactId;

    @Column(name = "bull_fee")
    private BigDecimal bullFee;

    @Column(name = "semen_cost")
    private BigDecimal semenCost;

    @Column(name = "fee_paid", nullable = false)
    private boolean feePaid;

    @Column(name = "expected_due_date")
    private LocalDate expectedDueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "pregnancy_status", nullable = false)
    private Pregnancy pregnancyStatus = Pregnancy.UNKNOWN;

    @Column(name = "preg_check_date")
    private LocalDate pregCheckDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "preg_check_method")
    private CheckMethod pregCheckMethod;

    @Column(name = "preg_check_cost")
    private BigDecimal pregCheckCost;

    @Column(name = "preg_checked_by_contact_id")
    private Long pregCheckedByContactId;

    @Column(name = "calving_date")
    private LocalDate calvingDate;

    @Column(name = "calving_time")
    private LocalTime calvingTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "calving_outcome")
    private Outcome calvingOutcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "calving_ease")
    private Ease calvingEase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Calf> calves = new ArrayList<>();

    @Column(name = "colostrum_given_at")
    private LocalTime colostrumGivenAt;

    @Column(name = "colostrum_litres")
    private BigDecimal colostrumLitres;

    @Column(name = "retained_placenta", nullable = false)
    private boolean retainedPlacenta;

    private String notes;

    protected BreedingRecord() {
    }

    public BreedingRecord(Long farmId, Long damId) {
        super(farmId);
        this.damId = damId;
    }

    /** Semen + technician + bull + pregnancy-check costs. */
    public BigDecimal totalCost() {
        BigDecimal t = BigDecimal.ZERO;
        for (BigDecimal b : new BigDecimal[] {semenCost, technicianFee, bullFee, pregCheckCost}) {
            if (b != null) {
                t = t.add(b);
            }
        }
        return t;
    }

    public Long getDamId() { return damId; }
    public int getLactationNumber() { return lactationNumber; }
    public void setLactationNumber(int lactationNumber) { this.lactationNumber = lactationNumber; }
    public Long getHeatEventId() { return heatEventId; }
    public void setHeatEventId(Long heatEventId) { this.heatEventId = heatEventId; }
    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }
    public LocalTime getServiceTime() { return serviceTime; }
    public void setServiceTime(LocalTime serviceTime) { this.serviceTime = serviceTime; }
    public Method getMethod() { return method; }
    public void setMethod(Method method) { this.method = method; }
    public Long getSemenItemId() { return semenItemId; }
    public void setSemenItemId(Long semenItemId) { this.semenItemId = semenItemId; }
    public Integer getStraws() { return straws; }
    public void setStraws(Integer straws) { this.straws = straws; }
    public Long getSireId() { return sireId; }
    public void setSireId(Long sireId) { this.sireId = sireId; }
    public String getSireTag() { return sireTag; }
    public void setSireTag(String sireTag) { this.sireTag = sireTag; }
    public LocalDate getBullExposureStart() { return bullExposureStart; }
    public void setBullExposureStart(LocalDate d) { this.bullExposureStart = d; }
    public LocalDate getBullExposureEnd() { return bullExposureEnd; }
    public void setBullExposureEnd(LocalDate d) { this.bullExposureEnd = d; }
    public Long getTechnicianContactId() { return technicianContactId; }
    public void setTechnicianContactId(Long id) { this.technicianContactId = id; }
    public BigDecimal getTechnicianFee() { return technicianFee; }
    public void setTechnicianFee(BigDecimal technicianFee) { this.technicianFee = technicianFee; }
    public Long getBullOwnerContactId() { return bullOwnerContactId; }
    public void setBullOwnerContactId(Long id) { this.bullOwnerContactId = id; }
    public BigDecimal getBullFee() { return bullFee; }
    public void setBullFee(BigDecimal bullFee) { this.bullFee = bullFee; }
    public BigDecimal getSemenCost() { return semenCost; }
    public void setSemenCost(BigDecimal semenCost) { this.semenCost = semenCost; }
    public boolean isFeePaid() { return feePaid; }
    public void setFeePaid(boolean feePaid) { this.feePaid = feePaid; }
    public LocalDate getExpectedDueDate() { return expectedDueDate; }
    public void setExpectedDueDate(LocalDate expectedDueDate) { this.expectedDueDate = expectedDueDate; }
    public Pregnancy getPregnancyStatus() { return pregnancyStatus; }
    public void setPregnancyStatus(Pregnancy pregnancyStatus) { this.pregnancyStatus = pregnancyStatus; }
    public LocalDate getPregCheckDate() { return pregCheckDate; }
    public void setPregCheckDate(LocalDate pregCheckDate) { this.pregCheckDate = pregCheckDate; }
    public CheckMethod getPregCheckMethod() { return pregCheckMethod; }
    public void setPregCheckMethod(CheckMethod pregCheckMethod) { this.pregCheckMethod = pregCheckMethod; }
    public BigDecimal getPregCheckCost() { return pregCheckCost; }
    public void setPregCheckCost(BigDecimal pregCheckCost) { this.pregCheckCost = pregCheckCost; }
    public Long getPregCheckedByContactId() { return pregCheckedByContactId; }
    public void setPregCheckedByContactId(Long id) { this.pregCheckedByContactId = id; }
    public LocalDate getCalvingDate() { return calvingDate; }
    public void setCalvingDate(LocalDate calvingDate) { this.calvingDate = calvingDate; }
    public LocalTime getCalvingTime() { return calvingTime; }
    public void setCalvingTime(LocalTime calvingTime) { this.calvingTime = calvingTime; }
    public Outcome getCalvingOutcome() { return calvingOutcome; }
    public void setCalvingOutcome(Outcome calvingOutcome) { this.calvingOutcome = calvingOutcome; }
    public Ease getCalvingEase() { return calvingEase; }
    public void setCalvingEase(Ease calvingEase) { this.calvingEase = calvingEase; }
    public List<Calf> getCalves() { return calves == null ? List.of() : calves; }
    public void setCalves(List<Calf> calves) { this.calves = new ArrayList<>(calves); }
    public LocalTime getColostrumGivenAt() { return colostrumGivenAt; }
    public void setColostrumGivenAt(LocalTime colostrumGivenAt) { this.colostrumGivenAt = colostrumGivenAt; }
    public BigDecimal getColostrumLitres() { return colostrumLitres; }
    public void setColostrumLitres(BigDecimal colostrumLitres) { this.colostrumLitres = colostrumLitres; }
    public boolean isRetainedPlacenta() { return retainedPlacenta; }
    public void setRetainedPlacenta(boolean retainedPlacenta) { this.retainedPlacenta = retainedPlacenta; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
