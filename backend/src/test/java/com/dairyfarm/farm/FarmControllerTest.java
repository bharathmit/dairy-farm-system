package com.dairyfarm.farm;

import static com.dairyfarm.support.TestTokens.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.dairyfarm.support.Api;
import com.dairyfarm.support.IntegrationTest;
import com.dairyfarm.support.TestTokens.TestUser;

class FarmControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private final TestUser stranger = TestUser.random("Stranger");
    private long farmId;

    @BeforeEach
    void setUp() throws Exception {
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
    }

    @Test
    void signUpSentSeveralTimesAtOnceCreatesOneFarm() throws Exception {
        TestUser newOwner = TestUser.random("Green Owner");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        var start = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.ArrayList<java.util.concurrent.Future<Long>>();
        for (int i = 0; i < 3; i++) {
            calls.add(pool.submit(() -> {
                start.await();
                return Api.createFarm(mvc, json, newOwner, "Green");
            }));
        }
        start.countDown();
        var ids = new java.util.HashSet<Long>();
        for (var c : calls) {
            ids.add(c.get(30, java.util.concurrent.TimeUnit.SECONDS));
        }
        pool.shutdown();

        org.assertj.core.api.Assertions.assertThat(ids).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from farm where lower(name) = 'green'", Long.class)).isEqualTo(1);

        // A differently named farm is still a new farm.
        org.assertj.core.api.Assertions.assertThat(Api.createFarm(mvc, json, newOwner, "Green Hills"))
                .isNotIn(ids);
    }

    @Test
    void creatorBecomesOwnerAndSeesTheFarm() throws Exception {
        mvc.perform(get("/api/v1/me").with(as(owner)))
                .andExpect(jsonPath("$.farms[0].name").value("Villupuram Dairy"))
                .andExpect(jsonPath("$.farms[0].role").value("OWNER"))
                .andExpect(jsonPath("$.farms[0].district").value("Villupuram"));

        mvc.perform(get("/api/v1/farms/{id}/members", farmId).with(as(owner)))
                .andExpect(jsonPath("$[0].name").value("Bharath"))
                .andExpect(jsonPath("$[0].loginType").value("SELF"))
                .andExpect(jsonPath("$[0].me").value(true));
    }

    @Test
    void ownerUpdatesFarmButWorkerCannot() throws Exception {
        String body = "{\"name\":\"Villupuram Organic Dairy\",\"village\":\"Kandamangalam\",\"district\":\"Villupuram\"}";
        mvc.perform(put("/api/v1/farms/{id}", farmId).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Villupuram Organic Dairy"))
                .andExpect(jsonPath("$.village").value("Kandamangalam"));

        mvc.perform(put("/api/v1/farms/{id}", farmId).with(as(worker))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only farm owners can do this."));
    }

    @Test
    void nonMembersCannotSeeTheFarm() throws Exception {
        mvc.perform(get("/api/v1/farms/{id}", farmId).with(as(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/farms/{id}/settings", farmId).with(as(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void settingsStartWithDefaultsAndMergePartialUpdates() throws Exception {
        mvc.perform(get("/api/v1/farms/{id}/settings", farmId).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.morningMilking").value("06:00"))
                .andExpect(jsonPath("$.eveningMilking").value("18:00"))
                .andExpect(jsonPath("$.marginAlertPct").value(30))
                .andExpect(jsonPath("$.morningFeeding").value("07:00"))
                .andExpect(jsonPath("$.eveningFeeding").value("19:00"))
                .andExpect(jsonPath("$.feedPushUps[0]").value("09:00"))
                .andExpect(jsonPath("$.feedPushUps.length()").value(3))
                .andExpect(jsonPath("$.notifications.leadDays").value(1));

        mvc.perform(put("/api/v1/farms/{id}/settings", farmId).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"milkRate\":38.5,\"morningMilking\":\"05:30\",\"eveningFeeding\":\"18:00\","
                                + "\"feedPushUps\":[\"21:00\",\"09:00\"],\"notifications\":{\"digest\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milkRate").value(38.5))
                .andExpect(jsonPath("$.morningMilking").value("05:30"))
                .andExpect(jsonPath("$.eveningMilking").value("18:00"))
                .andExpect(jsonPath("$.eveningFeeding").value("18:00"))
                .andExpect(jsonPath("$.feedPushUps[0]").value("09:00"))
                .andExpect(jsonPath("$.feedPushUps[1]").value("21:00"))
                .andExpect(jsonPath("$.notifications.digest").value(false))
                .andExpect(jsonPath("$.notifications.enabled").value(true));

        mvc.perform(get("/api/v1/farms/{id}/settings", farmId).with(as(owner)))
                .andExpect(jsonPath("$.milkRate").value(38.5))
                .andExpect(jsonPath("$.morningMilking").value("05:30"));
    }

    @Test
    void settingsAreValidatedAndOwnerOnly() throws Exception {
        mvc.perform(put("/api/v1/farms/{id}/settings", farmId).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marginAlertPct\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.marginAlertPct").exists());

        mvc.perform(put("/api/v1/farms/{id}/settings", farmId).with(as(worker))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"milkRate\":40}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileAndPreferencesBelongToThePerson() throws Exception {
        mvc.perform(put("/api/v1/me").with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Bharath Kumar\",\"phone\":\"+65 8123 4567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Bharath Kumar"));

        mvc.perform(put("/api/v1/me/preferences").with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"TA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("TA"))
                .andExpect(jsonPath("$.theme").value("SYSTEM"));

        mvc.perform(get("/api/v1/me").with(as(worker)))
                .andExpect(jsonPath("$.preferences.language").value("EN"));
    }
}
