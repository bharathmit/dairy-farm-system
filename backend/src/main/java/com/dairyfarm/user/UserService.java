package com.dairyfarm.user;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.member.FarmMemberRepository;
import com.dairyfarm.security.CurrentUser;

@Service
public class UserService {

    private final AppUserRepository users;
    private final FarmMemberRepository members;

    public UserService(AppUserRepository users, FarmMemberRepository members) {
        this.users = users;
        this.members = members;
    }

    /**
     * Makes sure the signed-in person has an app_user row. On the first call, team memberships that
     * an owner created with this email (invites, or logins made before this person signed in) are
     * linked to the account.
     */
    @Transactional
    public void ensureUser(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        if (users.existsById(id)) {
            return;
        }
        String email = CurrentUser.email(jwt);
        int created = users.insertIfAbsent(id, email, CurrentUser.fullName(jwt));
        if (created == 1 && email != null) {
            members.linkPendingByEmail(id, email);
        }
    }

    @Transactional(readOnly = true)
    public AppUser current() {
        return users.findById(CurrentUser.id())
                .orElseThrow(() -> ApiException.notFound("Account not found."));
    }

    @Transactional
    public AppUser updateProfile(ProfileRequest req) {
        AppUser u = current();
        u.setFullName(req.fullName().trim());
        u.setPhone(blankToNull(req.phone()));
        return users.save(u);
    }

    @Transactional
    public AppUser updatePreferences(UserPreferences prefs) {
        AppUser u = current();
        var base = u.getPreferences();
        u.setPreferences(new UserPreferences(
                prefs.theme() != null ? prefs.theme() : base.theme(),
                prefs.language() != null ? prefs.language() : base.language()));
        return users.save(u);
    }

    /**
     * Makes sure a row exists for an account created on someone's behalf (owner-set password),
     * so the membership can point at it before that person first signs in.
     */
    @Transactional
    public void ensureUser(UUID id, String email, String fullName) {
        users.insertIfAbsent(id, email, fullName);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
