package com.dairyfarm.member;

import java.util.UUID;

/** Account management in Supabase Auth, done by the server with the service key. */
public interface AuthAdmin {

    /** @param userId the new account's id, or null when an account with this email already exists */
    record CreatedUser(UUID userId) {
        public boolean alreadyExisted() {
            return userId == null;
        }
    }

    enum InviteResult { INVITED, ALREADY_REGISTERED }

    /** Creates a confirmed email + password account. */
    CreatedUser createUser(String email, String password, String fullName);

    /** Emails an invite link; the person sets their own password. */
    InviteResult invite(String email, String fullName);

    /** Replaces the password of an account the farm created. */
    void setPassword(UUID userId, String password);
}
