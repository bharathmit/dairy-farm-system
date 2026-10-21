package com.dairyfarm.support;

import static com.dairyfarm.support.TestTokens.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dairyfarm.support.TestTokens.TestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Small helpers for setting up data through the API in tests. */
public final class Api {

    private Api() {
    }

    public static long createFarm(MockMvc mvc, JsonMapper json, TestUser owner, String name) throws Exception {
        String body = mvc.perform(post("/api/v1/farms").with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"district\":\"Villupuram\",\"state\":\"Tamil Nadu\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    /** Adds a worker who signs in with the given account (linked directly in the database). */
    public static void addWorker(org.springframework.jdbc.core.JdbcTemplate jdbc, long farmId, TestUser worker) {
        jdbc.update("insert into app_user (id, email, full_name) values (?, ?, ?) on conflict do nothing",
                worker.id(), worker.email(), worker.name());
        jdbc.update("insert into farm_member (farm_id, user_id, name, email, role, login_type) values (?, ?, ?, ?, 'WORKER', 'PASSWORD')",
                farmId, worker.id(), worker.name(), worker.email());
    }

    public static JsonNode read(JsonMapper json, String body) throws Exception {
        return json.readTree(body);
    }
}
