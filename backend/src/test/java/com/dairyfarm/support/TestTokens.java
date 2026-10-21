package com.dairyfarm.support;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/** Signs Supabase-style access tokens for tests (HS256 with the test secret). */
public final class TestTokens {

    public static final String SUPABASE_URL = "http://supabase.test";
    public static final String SECRET = "test-secret-test-secret-test-secret-0123456789";

    private TestTokens() {
    }

    public static String token(UUID userId, String email, String name) {
        return token(userId, email, name, "authenticated", SECRET);
    }

    public static String token(UUID userId, String email, String name, String audience, String secret) {
        try {
            var claims = new JWTClaimsSet.Builder()
                    .issuer(SUPABASE_URL + "/auth/v1")
                    .subject(userId.toString())
                    .audience(audience)
                    .claim("email", email)
                    .claim("role", "authenticated")
                    .claim("user_metadata", name == null ? Map.of() : Map.of("full_name", name))
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .build();
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret.getBytes()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Adds "Authorization: Bearer ..." for the given person. */
    public static RequestPostProcessor as(TestUser user) {
        String token = token(user.id(), user.email(), user.name());
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    public record TestUser(UUID id, String email, String name) {
        public static TestUser random(String name) {
            String email = name.toLowerCase().replace(' ', '.') + "@example.com";
            return new TestUser(UUID.randomUUID(), email, name);
        }
    }
}
