package com.dairyfarm.user;

import static com.dairyfarm.support.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dairyfarm.support.IntegrationTest;
import com.dairyfarm.support.TestTokens;
import com.dairyfarm.support.TestTokens.TestUser;

class MeControllerTest extends IntegrationTest {

    private final TestUser bharath = TestUser.random("Bharath");

    @Test
    void rejectsRequestsWithoutAToken() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokensForTheWrongAudienceOrKey() throws Exception {
        String wrongAud = TestTokens.token(bharath.id(), bharath.email(), "B", "anon", TestTokens.SECRET);
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + wrongAud))
                .andExpect(status().isUnauthorized());

        String wrongKey = TestTokens.token(bharath.id(), bharath.email(), "B", "authenticated",
                "another-secret-another-secret-another-secret-01");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + wrongKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsTheAccountOnFirstCallAndReturnsIt() throws Exception {
        mvc.perform(get("/api/v1/me").with(as(bharath)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bharath.id().toString()))
                .andExpect(jsonPath("$.email").value(bharath.email()))
                .andExpect(jsonPath("$.fullName").value("Bharath"))
                .andExpect(jsonPath("$.preferences.theme").value("SYSTEM"))
                .andExpect(jsonPath("$.preferences.language").value("EN"))
                .andExpect(jsonPath("$.farms").isEmpty());

        mvc.perform(get("/api/v1/me").with(as(bharath))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from app_user", Long.class)).isEqualTo(1);
    }

    @Test
    void linksAMembershipAddedByEmailBeforeTheFirstSignIn() throws Exception {
        UUID owner = UUID.randomUUID();
        jdbc.update("insert into app_user (id, email) values (?, 'owner@example.com')", owner);
        Long farmId = jdbc.queryForObject("insert into farm (name) values ('Villupuram Dairy') returning id", Long.class);
        jdbc.update("insert into farm_member (farm_id, user_id, name, role, login_type) values (?, ?, 'Owner', 'OWNER', 'SELF')",
                farmId, owner);
        jdbc.update("insert into farm_member (farm_id, name, email, role, login_type) values (?, 'Murugan', 'MURUGAN@example.com', 'WORKER', 'INVITE')",
                farmId);

        TestUser murugan = new TestUser(UUID.randomUUID(), "murugan@example.com", "Murugan");
        mvc.perform(get("/api/v1/me").with(as(murugan)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farms[0].name").value("Villupuram Dairy"))
                .andExpect(jsonPath("$.farms[0].role").value("WORKER"));
    }

    @Test
    void doesNotLinkAWorkerWhoWasNotGivenASignIn() throws Exception {
        Long farmId = jdbc.queryForObject("insert into farm (name) values ('Villupuram Dairy') returning id", Long.class);
        jdbc.update("insert into farm_member (farm_id, name, email, role, login_type) values (?, 'Selvi', 'selvi@example.com', 'WORKER', 'NONE')",
                farmId);

        TestUser selvi = new TestUser(UUID.randomUUID(), "selvi@example.com", "Selvi");
        mvc.perform(get("/api/v1/me").with(as(selvi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farms").isEmpty());
    }
}
