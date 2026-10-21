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
}
