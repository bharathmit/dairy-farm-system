package com.dairyfarm.farm;

import java.math.BigDecimal;

import com.dairyfarm.member.Role;

/** A farm with its location and the caller's role in it. */
public record FarmResponse(Long id, String name, String village, String district, String state, String country,
                           BigDecimal latitude, BigDecimal longitude, Role role) {

    static FarmResponse of(Farm f, Role role) {
        return new FarmResponse(f.getId(), f.getName(), f.getVillage(), f.getDistrict(), f.getState(),
                f.getCountry(), f.getLatitude(), f.getLongitude(), role);
    }
}
