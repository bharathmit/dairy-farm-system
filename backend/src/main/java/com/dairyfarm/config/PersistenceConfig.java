package com.dairyfarm.config;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.dairyfarm.security.CurrentUser;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditor")
public class PersistenceConfig {

    /** Fills created_by with the signed-in user's id. */
    @Bean
    AuditorAware<UUID> auditor() {
        return () -> Optional.ofNullable(CurrentUser.idOrNull());
    }

    /** JSONB columns use the same Jackson settings as the API (ISO dates, times as "06:00"). */
    @Bean
    HibernatePropertiesCustomizer jsonFormatMapper(JsonMapper jsonMapper) {
        return props -> props.put(AvailableSettings.JSON_FORMAT_MAPPER, new Jackson3JsonFormatMapper(jsonMapper));
    }
}
