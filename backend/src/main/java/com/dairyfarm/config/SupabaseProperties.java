package com.dairyfarm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Connection details for the Supabase project (auth, admin API, storage). */
@ConfigurationProperties("app.supabase")
public record SupabaseProperties(
        String url,
        String jwtSecret,
        String serviceRoleKey,
        String photoBucket,
        String inviteRedirectUrl) {

    public String baseUrl() {
        return url == null ? "" : url.replaceAll("/+$", "");
    }

    /** Value of the "iss" claim in Supabase access tokens. */
    public String issuer() {
        return baseUrl() + "/auth/v1";
    }

    public boolean hasUrl() {
        return StringUtils.hasText(url);
    }

    public boolean hasJwtSecret() {
        return StringUtils.hasText(jwtSecret);
    }

    /** True when the server can call the admin and storage APIs. */
    public boolean hasServiceRoleKey() {
        return hasUrl() && StringUtils.hasText(serviceRoleKey);
    }
}
