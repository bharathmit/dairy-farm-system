package com.dairyfarm.member;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.config.SupabaseProperties;

/** Calls the Supabase Auth admin API (/auth/v1/admin/users, /auth/v1/invite). */
@Component
class SupabaseAuthAdmin implements AuthAdmin {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthAdmin.class);

    private final SupabaseProperties props;
    private final RestClient rest;

    SupabaseAuthAdmin(SupabaseProperties props, RestClient.Builder builder) {
        this.props = props;
        this.rest = builder.build();
    }

    @Override
    public CreatedUser createUser(String email, String password, String fullName) {
        var body = Map.of(
                "email", email,
                "password", password,
                "email_confirm", true,
                "user_metadata", Map.of("full_name", fullName));
        try {
            Map<?, ?> user = rest.post().uri(url("/admin/users")).headers(this::auth)
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Map.class);
            return new CreatedUser(UUID.fromString(String.valueOf(user.get("id"))));
        } catch (RestClientResponseException e) {
            if (alreadyExists(e)) {
                return new CreatedUser(null);
            }
            throw translate(e, "create the sign-in");
        } catch (RestClientException e) {
            throw unreachable(e);
        }
    }

    @Override
    public InviteResult invite(String email, String fullName) {
        var uri = UriComponentsBuilder.fromUriString(url("/invite"));
        if (StringUtils.hasText(props.inviteRedirectUrl())) {
            uri.queryParam("redirect_to", props.inviteRedirectUrl());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("data", Map.of("full_name", fullName));
        try {
            rest.post().uri(uri.build().toUri()).headers(this::auth)
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
            return InviteResult.INVITED;
        } catch (RestClientResponseException e) {
            if (alreadyExists(e)) {
                return InviteResult.ALREADY_REGISTERED;
            }
            throw translate(e, "send the invite");
        } catch (RestClientException e) {
            throw unreachable(e);
        }
    }

    @Override
    public void setPassword(UUID userId, String password) {
        try {
            rest.put().uri(url("/admin/users/" + userId)).headers(this::auth)
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("password", password))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw translate(e, "change the password");
        } catch (RestClientException e) {
            throw unreachable(e);
        }
    }

    private String url(String path) {
        if (!props.hasServiceRoleKey()) {
            throw ApiException.unavailable(
                    "Sign-ins for team members aren't set up on the server yet (SUPABASE_SERVICE_ROLE_KEY is missing).");
        }
        return props.issuer() + path;
    }

    private void auth(HttpHeaders h) {
        String key = props.serviceRoleKey();
        h.set("apikey", key);
        if (key.startsWith("eyJ")) {
            // Legacy service_role JWT. New "sb_secret_" keys go in the apikey header only.
            h.setBearerAuth(key);
        }
    }

    private static boolean alreadyExists(RestClientResponseException e) {
        String body = e.getResponseBodyAsString().toLowerCase();
        return body.contains("email_exists") || body.contains("already been registered")
                || body.contains("already registered");
    }

    private static ApiException translate(RestClientResponseException e, String action) {
        String body = e.getResponseBodyAsString().toLowerCase();
        if (body.contains("weak_password") || body.contains("password should")) {
            return ApiException.badRequest("That password is too weak. Use at least 8 characters with letters and numbers.");
        }
        if (body.contains("email_address_invalid") || body.contains("invalid email")) {
            return ApiException.badRequest("That email address isn't valid.");
        }
        if (e.getStatusCode().value() == 429) {
            return new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Too many emails sent in a short time. Wait a few minutes and try again.");
        }
        log.warn("Supabase Auth admin call failed ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
        return ApiException.unavailable("Couldn't " + action + " right now. Try again in a moment.");
    }

    private static ApiException unreachable(RestClientException e) {
        log.warn("Supabase Auth unreachable: {}", e.getMessage());
        return ApiException.unavailable("Couldn't reach the sign-in service. Try again in a moment.");
    }
}
