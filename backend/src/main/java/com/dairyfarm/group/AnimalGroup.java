package com.dairyfarm.group;

import com.dairyfarm.common.FarmScoped;
import com.dairyfarm.common.Species;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A pen, shed or herd group. All animals in a group are one species. */
@Entity
@Table(name = "animal_group")
public class AnimalGroup extends FarmScoped {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Species species;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_outcome", nullable = false)
    private GroupPurpose purpose = GroupPurpose.MAINTENANCE;

    @Column(name = "manager_member_id")
    private Long managerMemberId;

    private String colour;

    private String notes;

    protected AnimalGroup() {
    }

    public AnimalGroup(Long farmId, String name, Species species) {
        super(farmId);
        this.name = name;
        this.species = species;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Species getSpecies() {
        return species;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public GroupPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(GroupPurpose purpose) {
        this.purpose = purpose;
    }

    public Long getManagerMemberId() {
        return managerMemberId;
    }

    public void setManagerMemberId(Long managerMemberId) {
        this.managerMemberId = managerMemberId;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
