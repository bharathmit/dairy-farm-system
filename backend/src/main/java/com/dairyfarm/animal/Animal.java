package com.dairyfarm.animal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dairyfarm.animal.AnimalEnums.Acquisition;
import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.animal.AnimalEnums.Status;
import com.dairyfarm.common.FarmScoped;
import com.dairyfarm.common.Sex;
import com.dairyfarm.common.Species;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "animal")
public class Animal extends FarmScoped {

    @Column(nullable = false)
    private String tag;

    private String name;

    @Column(name = "national_id")
    private String nationalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Species species;

    private String breed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sex sex;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private String colour;

    @Column(name = "birth_weight_kg")
    private BigDecimal birthWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "dam_id")
    private Long damId;

    @Column(name = "sire_id")
    private Long sireId;

    @Column(name = "dam_tag")
    private String damTag;

    @Column(name = "sire_tag")
    private String sireTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquisition_type", nullable = false)
    private Acquisition acquisitionType;

    @Column(name = "acquisition_date")
    private LocalDate acquisitionDate;

    @Column(name = "purchase_price")
    private BigDecimal purchasePrice;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "exit_reason")
    private String exitReason;

    @Column(name = "exit_price")
    private BigDecimal exitPrice;

    @Column(name = "exit_party")
    private String exitParty;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "photo_paths", nullable = false, columnDefinition = "text[]")
    private List<String> photoPaths = new ArrayList<>();

    @Column(name = "last_calving_date")
    private LocalDate lastCalvingDate;

    @Column(name = "current_weight_kg")
    private BigDecimal currentWeightKg;

    private String notes;

    protected Animal() {
    }

    public Animal(Long farmId, Species species) {
        super(farmId);
        this.species = species;
    }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public Species getSpecies() { return species; }
    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }
    public Sex getSex() { return sex; }
    public void setSex(Sex sex) { this.sex = sex; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getColour() { return colour; }
    public void setColour(String colour) { this.colour = colour; }
    public BigDecimal getBirthWeightKg() { return birthWeightKg; }
    public void setBirthWeightKg(BigDecimal birthWeightKg) { this.birthWeightKg = birthWeightKg; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getDamId() { return damId; }
    public void setDamId(Long damId) { this.damId = damId; }
    public Long getSireId() { return sireId; }
    public void setSireId(Long sireId) { this.sireId = sireId; }
    public String getDamTag() { return damTag; }
    public void setDamTag(String damTag) { this.damTag = damTag; }
    public String getSireTag() { return sireTag; }
    public void setSireTag(String sireTag) { this.sireTag = sireTag; }
    public Acquisition getAcquisitionType() { return acquisitionType; }
    public void setAcquisitionType(Acquisition acquisitionType) { this.acquisitionType = acquisitionType; }
    public LocalDate getAcquisitionDate() { return acquisitionDate; }
    public void setAcquisitionDate(LocalDate acquisitionDate) { this.acquisitionDate = acquisitionDate; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    public LocalDate getExitDate() { return exitDate; }
    public void setExitDate(LocalDate exitDate) { this.exitDate = exitDate; }
    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }
    public BigDecimal getExitPrice() { return exitPrice; }
    public void setExitPrice(BigDecimal exitPrice) { this.exitPrice = exitPrice; }
    public String getExitParty() { return exitParty; }
    public void setExitParty(String exitParty) { this.exitParty = exitParty; }
    public List<String> getPhotoPaths() { return photoPaths; }
    public void setPhotoPaths(List<String> photoPaths) { this.photoPaths = new ArrayList<>(photoPaths); }
    public LocalDate getLastCalvingDate() { return lastCalvingDate; }
    public void setLastCalvingDate(LocalDate lastCalvingDate) { this.lastCalvingDate = lastCalvingDate; }
    public BigDecimal getCurrentWeightKg() { return currentWeightKg; }
    public void setCurrentWeightKg(BigDecimal currentWeightKg) { this.currentWeightKg = currentWeightKg; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
