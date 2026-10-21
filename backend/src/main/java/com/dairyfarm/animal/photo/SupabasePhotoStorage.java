package com.dairyfarm.animal.photo;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.config.SupabaseProperties;

/** Supabase Storage REST API, called with the service key. The bucket stays private. */
@Component
class SupabasePhotoStorage implements PhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(SupabasePhotoStorage.class);
    private static final int LINK_SECONDS = 3600;

    private final SupabaseProperties props;
    private final RestClient rest;

    SupabasePhotoStorage(SupabaseProperties props, RestClient.Builder builder) {
        this.props = props;
        this.rest = builder.build();
    }

    @Override
    public void upload(String path, byte[] data, String contentType) {
        requireConfigured();
        try {
            rest.post().uri(base() + "/object/" + props.photoBucket() + "/" + path)
                    .headers(this::auth)
                    .header("x-upsert", "false")
                    .header("cache-control", "max-age=31536000")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Photo upload failed: {}", e.getMessage());
            throw ApiException.unavailable("Couldn't save the photo right now. Try again in a moment.");
        }
    }

    @Override
    public void delete(Collection<String> paths) {
        if (paths.isEmpty() || !props.hasServiceRoleKey()) {
            return;
        }
        try {
            rest.method(HttpMethod.DELETE).uri(base() + "/object/" + props.photoBucket())
                    .headers(this::auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefixes", List.copyOf(paths)))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Photo delete failed for {}: {}", paths, e.getMessage());
        }
    }

    @Override
    public Map<String, String> signedUrls(Collection<String> paths) {
        if (paths.isEmpty() || !props.hasServiceRoleKey()) {
            return Map.of();
        }
        try {
            List<?> rows = rest.post().uri(base() + "/object/sign/" + props.photoBucket())
                    .headers(this::auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", LINK_SECONDS, "paths", List.copyOf(paths)))
                    .retrieve().body(List.class);
            Map<String, String> urls = new HashMap<>();
            if (rows != null) {
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> m && m.get("signedURL") instanceof String signed && m.get("path") instanceof String p) {
                        urls.put(p, base() + signed);
                    }
                }
            }
            return urls;
        } catch (RestClientException e) {
            log.warn("Signing photo links failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private String base() {
        return props.baseUrl() + "/storage/v1";
    }

    private void requireConfigured() {
        if (!props.hasServiceRoleKey()) {
            throw ApiException.unavailable("Photo storage isn't set up on the server yet (SUPABASE_SERVICE_ROLE_KEY is missing).");
        }
    }

    private void auth(HttpHeaders h) {
        String key = props.serviceRoleKey();
        h.set("apikey", key);
        if (key.startsWith("eyJ")) {
            h.setBearerAuth(key);
        }
    }
}
