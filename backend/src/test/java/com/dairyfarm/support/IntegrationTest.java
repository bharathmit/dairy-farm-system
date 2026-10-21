package com.dairyfarm.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.json.JsonMapper;

/**
 * Full application against a real Postgres. Uses Testcontainers, or an existing database when
 * TEST_DATABASE_URL (plus TEST_DATABASE_USER / TEST_DATABASE_PASSWORD) is set.
 */
@SpringBootTest(properties = {
        "app.supabase.url=" + TestTokens.SUPABASE_URL,
        "app.supabase.jwt-secret=" + TestTokens.SECRET,
        "app.supabase.service-role-key=test-service-key",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("TEST_DATABASE_URL");
    private static final PostgreSQLContainer POSTGRES;

    static {
        if (EXTERNAL_URL == null) {
            POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> envOr("TEST_DATABASE_USER", "postgres"));
            registry.add("spring.datasource.password", () -> envOr("TEST_DATABASE_PASSWORD", ""));
        }
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return v == null ? fallback : v;
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE app_user, farm RESTART IDENTITY CASCADE");
    }
}
