package com.dairyfarm.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/** A row that belongs to one farm. The farm never changes after the row is created. */
@MappedSuperclass
public abstract class FarmScoped extends Auditable {

    @Column(name = "farm_id", nullable = false, updatable = false)
    private Long farmId;

    protected FarmScoped() {
    }

    protected FarmScoped(Long farmId) {
        this.farmId = farmId;
    }

    public Long getFarmId() {
        return farmId;
    }
}
