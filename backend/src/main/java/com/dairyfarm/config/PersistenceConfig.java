package com.dairyfarm.config;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.dairyfarm.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    HibernatePropertiesCustomizer jsonFormatMapper(ObjectMapper objectMapper) {
        return props -> props.put(AvailableSettings.JSON_FORMAT_MAPPER, new JacksonJsonFormatMapper(objectMapper));
    }
}
