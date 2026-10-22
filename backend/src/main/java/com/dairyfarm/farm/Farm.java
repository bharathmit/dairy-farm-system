package com.dairyfarm.farm;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dairyfarm.common.Auditable;
import com.dairyfarm.milk.MilkGradingRules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "farm")
public class Farm extends Auditable {

    @Column(nullable = false)
    private String name;

    private String village;
    private String district;
    private String state;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private FarmSettings settings = FarmSettings.DEFAULTS;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "milk_grading", columnDefinition = "jsonb")
    private MilkGradingRules milkGrading;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "breeding_settings", columnDefinition = "jsonb")
    private com.dairyfarm.breeding.BreedingSettings breedingSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alert_rules", columnDefinition = "jsonb")
    private java.util.Map<String, com.dairyfarm.alert.RuleSetting> alertRules;

    @Column(name = "alerts_refreshed_at")
    private java.time.Instant alertsRefreshedAt;

    @Column(name = "schedules_seeded", nullable = false)
    private boolean schedulesSeeded;

    protected Farm() {
    }

    public Farm(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVillage() {
        return village;
    }

    public void setVillage(String village) {
        this.village = village;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public FarmSettings getSettings() {
        return settings == null ? FarmSettings.DEFAULTS : settings.resolved();
    }

    public void setSettings(FarmSettings settings) {
        this.settings = settings;
    }

    public MilkGradingRules getMilkGrading() {
        return milkGrading == null ? MilkGradingRules.DEFAULTS : milkGrading.resolved();
    }

    public void setMilkGrading(MilkGradingRules milkGrading) {
        this.milkGrading = milkGrading;
    }

    public com.dairyfarm.breeding.BreedingSettings getBreedingSettings() {
        return breedingSettings == null ? com.dairyfarm.breeding.BreedingSettings.DEFAULTS.resolved() : breedingSettings.resolved();
    }

    public void setBreedingSettings(com.dairyfarm.breeding.BreedingSettings breedingSettings) {
        this.breedingSettings = breedingSettings;
    }

    public java.util.Map<String, com.dairyfarm.alert.RuleSetting> getAlertRules() {
        return alertRules == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(alertRules);
    }

    public void setAlertRules(java.util.Map<String, com.dairyfarm.alert.RuleSetting> alertRules) {
        this.alertRules = alertRules;
    }

    public java.time.Instant getAlertsRefreshedAt() {
        return alertsRefreshedAt;
    }

    public void setAlertsRefreshedAt(java.time.Instant alertsRefreshedAt) {
        this.alertsRefreshedAt = alertsRefreshedAt;
    }

    public boolean isSchedulesSeeded() {
        return schedulesSeeded;
    }

    public void setSchedulesSeeded(boolean schedulesSeeded) {
        this.schedulesSeeded = schedulesSeeded;
    }
}
