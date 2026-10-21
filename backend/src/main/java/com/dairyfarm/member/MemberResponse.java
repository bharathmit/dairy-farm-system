package com.dairyfarm.member;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dairyfarm.common.Sex;

/**
 * signIn: NONE (no login), PENDING (login set up, person hasn't signed in yet), ACTIVE (linked).
 * notice: a one-off message for the owner after a save, e.g. the email already had an account.
 */
public record MemberResponse(Long id, String name, Role role, String email, String phone, LocalDate dateOfBirth,
                             Sex sex, WageType wageType, BigDecimal wageRate, LocalDate joinedOn, LocalDate leftOn,
                             boolean active, LoginType loginType, SignIn signIn, Instant invitedAt, boolean me,
                             String notice) {

    public enum SignIn { NONE, PENDING, ACTIVE }

    static MemberResponse of(FarmMember m, UUID currentUser, String notice) {
        SignIn signIn = m.getUserId() != null ? SignIn.ACTIVE
                : m.getLoginType() == LoginType.NONE ? SignIn.NONE : SignIn.PENDING;
        return new MemberResponse(m.getId(), m.getName(), m.getRole(), m.getEmail(), m.getPhone(),
                m.getDateOfBirth(), m.getSex(), m.getWageType(), m.getWageRate(), m.getJoinedOn(), m.getLeftOn(),
                m.isActive(), m.getLoginType(), signIn, m.getInvitedAt(),
                m.getUserId() != null && m.getUserId().equals(currentUser), notice);
    }
}
