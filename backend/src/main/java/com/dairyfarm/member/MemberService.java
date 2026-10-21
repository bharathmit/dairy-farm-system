package com.dairyfarm.member;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.farm.FarmService;
import com.dairyfarm.security.CurrentUser;
import com.dairyfarm.user.AppUserRepository;
import com.dairyfarm.user.UserService;

@Service
public class MemberService {

    static final String EXISTING_ACCOUNT_NOTICE =
            "This email already has an account. They sign in with their own password and will see this farm.";

    private final FarmMemberRepository members;
    private final AppUserRepository users;
    private final UserService userService;
    private final AuthAdmin authAdmin;

    public MemberService(FarmMemberRepository members, AppUserRepository users, UserService userService,
                         AuthAdmin authAdmin) {
        this.members = members;
        this.users = users;
        this.userService = userService;
        this.authAdmin = authAdmin;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(Long farmId) {
        UUID me = CurrentUser.id();
        return members.findByFarmIdOrderByActiveDescRoleAscNameAsc(farmId).stream()
                .map(m -> MemberResponse.of(m, me, null)).toList();
    }

    @Transactional(readOnly = true)
    public MemberResponse get(Long farmId, Long id) {
        return MemberResponse.of(load(farmId, id), CurrentUser.id(), null);
    }

    @Transactional
    public MemberResponse create(Long farmId, MemberRequest req) {
        String email = normaliseEmail(req.email());
        if (email != null && members.existsByFarmIdAndEmailIgnoreCase(farmId, email)) {
            throw ApiException.conflict("Someone on this team already uses " + email + ".");
        }
        FarmMember m = new FarmMember(farmId, req.name().trim(), req.role());
        m.setEmail(email);
        applyDetails(m, req);
        if (m.getJoinedOn() == null) {
            m.setJoinedOn(LocalDate.now(FarmService.FARM_ZONE));
        }
        LoginType login = req.login() == null ? LoginType.NONE : req.login();
        String notice = setUpLogin(m, login, req.password());
        m = members.save(m);
        return MemberResponse.of(m, CurrentUser.id(), notice);
    }

    @Transactional
    public MemberResponse update(Long farmId, Long id, MemberRequest req) {
        FarmMember m = load(farmId, id);
        String email = normaliseEmail(req.email());
        if (!Objects.equals(lower(email), lower(m.getEmail()))) {
            if (m.getUserId() != null) {
                throw ApiException.conflict("Their email is tied to their sign-in and can't be changed here.");
            }
            if (email != null && members.existsByFarmIdAndEmailIgnoreCaseAndIdNot(farmId, email, id)) {
                throw ApiException.conflict("Someone on this team already uses " + email + ".");
            }
            m.setEmail(email);
        }
        if (m.getRole() == Role.OWNER && req.role() != Role.OWNER) {
            requireAnotherOwner(m, "change the role of");
        }
        m.setName(req.name().trim());
        m.setRole(req.role());
        applyDetails(m, req);

        String notice = null;
        LoginType login = req.login();
        boolean passwordChange = login == LoginType.PASSWORD && req.password() != null;
        if (login != null && (login != m.getLoginType() || passwordChange)) {
            if (login == LoginType.NONE && isMe(m)) {
                throw ApiException.badRequest("You can't turn off your own sign-in.");
            }
            if (login == LoginType.NONE && m.getRole() == Role.OWNER) {
                requireAnotherOwner(m, "turn off sign-in for");
            }
            notice = setUpLogin(m, login, req.password());
        }
        return MemberResponse.of(m, CurrentUser.id(), notice);
    }

    /** Marks someone as no longer working on the farm (or back again). */
    @Transactional
    public MemberResponse setActive(Long farmId, Long id, boolean active, LocalDate leftOn) {
        FarmMember m = load(farmId, id);
        if (!active && m.isActive()) {
            if (isMe(m)) {
                throw ApiException.badRequest("You can't mark yourself inactive. Ask another owner.");
            }
            if (m.getRole() == Role.OWNER) {
                requireAnotherOwner(m, "deactivate");
            }
            m.setActive(false);
            m.setLeftOn(leftOn != null ? leftOn : LocalDate.now(FarmService.FARM_ZONE));
        } else if (active && !m.isActive()) {
            m.setActive(true);
            m.setLeftOn(null);
        }
        return MemberResponse.of(m, CurrentUser.id(), null);
    }

    @Transactional
    public void delete(Long farmId, Long id) {
        FarmMember m = load(farmId, id);
        if (isMe(m)) {
            throw ApiException.badRequest("You can't remove yourself. Ask another owner.");
        }
        if (m.getRole() == Role.OWNER && m.isActive()) {
            requireAnotherOwner(m, "remove");
        }
        members.delete(m);
    }

    /** Sends the invite email again to someone who hasn't signed in yet. */
    @Transactional
    public MemberResponse resendInvite(Long farmId, Long id) {
        FarmMember m = load(farmId, id);
        if (m.getLoginType() != LoginType.INVITE || m.getUserId() != null) {
            throw ApiException.badRequest("There's no pending invite for " + m.getName() + ".");
        }
        String notice = invite(m);
        return MemberResponse.of(m, CurrentUser.id(), notice);
    }

    // ---------- sign-in ----------

    private String setUpLogin(FarmMember m, LoginType login, String password) {
        switch (login) {
            case NONE -> {
                m.setUserId(null);
                m.setLoginType(LoginType.NONE);
                m.setInvitedAt(null);
                return null;
            }
            case PASSWORD -> {
                return passwordLogin(m, password);
            }
            case INVITE -> {
                if (m.getUserId() != null) {
                    return m.getName() + " already signs in, so no invite was sent.";
                }
                return invite(m);
            }
            default -> throw ApiException.badRequest("Choose no sign-in, a password or an email invite.");
        }
    }

    private String passwordLogin(FarmMember m, String password) {
        if (m.getUserId() != null) {
            if (m.getLoginType() != LoginType.PASSWORD) {
                throw ApiException.badRequest(m.getName() + " manages their own password.");
            }
            if (password != null) {
                authAdmin.setPassword(m.getUserId(), password);
            }
            return null;
        }
        String email = requireEmail(m);
        if (password == null) {
            throw ApiException.badRequest("Set a password for their sign-in.");
        }
        var created = authAdmin.createUser(email, password, m.getName());
        if (created.alreadyExisted()) {
            linkExistingAccount(m);
            m.setLoginType(LoginType.SELF);
            return EXISTING_ACCOUNT_NOTICE;
        }
        userService.ensureUser(created.userId(), email, m.getName());
        attachUser(m, created.userId());
        m.setLoginType(LoginType.PASSWORD);
        m.setInvitedAt(null);
        return null;
    }

    private String invite(FarmMember m) {
        String email = requireEmail(m);
        var result = authAdmin.invite(email, m.getName());
        if (result == AuthAdmin.InviteResult.ALREADY_REGISTERED) {
            linkExistingAccount(m);
            m.setLoginType(LoginType.SELF);
            m.setInvitedAt(null);
            return EXISTING_ACCOUNT_NOTICE;
        }
        m.setLoginType(LoginType.INVITE);
        m.setInvitedAt(Instant.now());
        return "Invite sent to " + email + ".";
    }

    /** Links to the person's account now if they have used the app; otherwise on their first sign-in. */
    private void linkExistingAccount(FarmMember m) {
        users.findFirstByEmailIgnoreCase(m.getEmail()).ifPresent(u -> attachUser(m, u.getId()));
    }

    private void attachUser(FarmMember m, UUID userId) {
        if (members.existsByFarmIdAndUserId(m.getFarmId(), userId)) {
            throw ApiException.conflict("That person is already on this team.");
        }
        m.setUserId(userId);
    }

    // ---------- helpers ----------

    private FarmMember load(Long farmId, Long id) {
        return members.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> ApiException.notFound("Team member not found."));
    }

    private void requireAnotherOwner(FarmMember m, String action) {
        if (members.countByFarmIdAndRoleAndActiveTrue(m.getFarmId(), Role.OWNER) <= 1 && m.isActive()) {
            throw ApiException.conflict("A farm needs at least one owner. Add another owner before you "
                    + action + " " + m.getName() + ".");
        }
    }

    private static void applyDetails(FarmMember m, MemberRequest req) {
        m.setPhone(blankToNull(req.phone()));
        m.setDateOfBirth(req.dateOfBirth());
        m.setSex(req.sex());
        WageType wageType = req.wageType() == null ? WageType.NONE : req.wageType();
        m.setWageType(wageType);
        m.setWageRate(wageType == WageType.NONE ? null : req.wageRate());
        if (req.joinedOn() != null) {
            m.setJoinedOn(req.joinedOn());
        }
    }

    private static String requireEmail(FarmMember m) {
        if (m.getEmail() == null) {
            throw ApiException.badRequest("Add an email address so " + m.getName() + " can sign in.");
        }
        return m.getEmail();
    }

    private static boolean isMe(FarmMember m) {
        return m.getUserId() != null && m.getUserId().equals(CurrentUser.id());
    }

    private static String normaliseEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
