package com.dairyfarm.contact;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dairyfarm.common.FarmScoped;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Someone the farm deals with: milk buyer, vet, AI technician, supplier. */
@Entity
@Table(name = "contact")
public class Contact extends FarmScoped {

    public enum Role { BUYER, VET, AI_TECH, SUPPLIER, OTHER }

    public enum PayEvery { DELIVERY, WEEK, TEN_DAYS, FORTNIGHT, MONTH }

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> roles = new ArrayList<>();

    private String phone;
    private String village;

    @Column(name = "milk_rate")
    private BigDecimal milkRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_every")
    private PayEvery payEvery;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    protected Contact() {
    }

    public Contact(Long farmId) {
        super(farmId);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Role> getRoles() { return roles.stream().map(Role::valueOf).toList(); }
    public void setRoles(List<Role> roles) { this.roles = new ArrayList<>(roles.stream().distinct().map(Enum::name).toList()); }
    public boolean hasRole(Role role) { return roles.contains(role.name()); }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getVillage() { return village; }
    public void setVillage(String village) { this.village = village; }
    public BigDecimal getMilkRate() { return milkRate; }
    public void setMilkRate(BigDecimal milkRate) { this.milkRate = milkRate; }
    public PayEvery getPayEvery() { return payEvery; }
    public void setPayEvery(PayEvery payEvery) { this.payEvery = payEvery; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
