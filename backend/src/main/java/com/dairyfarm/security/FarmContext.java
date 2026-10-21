package com.dairyfarm.security;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.member.Role;

/**
 * The farm addressed by /api/v1/farms/{farmId}/... and the caller's membership in it.
 * Filled by FarmAccessInterceptor before the controller runs.
 */
@Component
@RequestScope
public class FarmContext {

    private Long farmId;
    private Long memberId;
    private Role role;

    void set(Long farmId, Long memberId, Role role) {
        this.farmId = farmId;
        this.memberId = memberId;
        this.role = role;
    }

    public Long farmId() {
        if (farmId == null) {
            throw new IllegalStateException("No farm in this request");
        }
        return farmId;
    }

    public Long memberId() {
        return memberId;
    }

    public Role role() {
        return role;
    }

    public boolean isOwner() {
        return role == Role.OWNER;
    }

    public void requireOwner() {
        if (!isOwner()) {
            throw ApiException.forbidden("Only farm owners can do this.");
        }
    }
}
