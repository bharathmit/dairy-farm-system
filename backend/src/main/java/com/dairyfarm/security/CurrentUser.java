package com.dairyfarm.security;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

import com.dairyfarm.common.ApiException;

/** Reads the signed-in Supabase user from the current access token. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }

    public static UUID id() {
        return UUID.fromString(jwt().getSubject());
    }

    public static UUID idOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return UUID.fromString(token.getToken().getSubject());
        }
        return null;
    }

    public static String email(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return StringUtils.hasText(email) ? email.trim() : null;
    }

    /** Name given at sign-up (Supabase keeps it in user_metadata). */
    public static String fullName(Jwt jwt) {
        Map<String, Object> meta = jwt.getClaimAsMap("user_metadata");
        if (meta != null) {
            for (String key : new String[] {"full_name", "name"}) {
                Object v = meta.get(key);
                if (v instanceof String s && StringUtils.hasText(s)) {
                    return s.trim();
                }
            }
        }
        return null;
    }
}
