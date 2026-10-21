package com.dairyfarm.record;

import static com.dairyfarm.support.TestTokens.as;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.dairyfarm.support.Api;
import com.dairyfarm.support.Daily;
import com.dairyfarm.support.IntegrationTest;
import com.dairyfarm.support.TestTokens.TestUser;

class MeasurementControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private long farmId;
    private long ganga;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = Daily.today();
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
        ganga = Daily.animal(mvc, json, owner, farmId, "VD-015", "HEIFER", "\"name\":\"Ganga\"");
    }

    private String url() {
        return "/api/v1/farms/" + farmId + "/animals/" + ganga + "/measurements";
    }

    private String log(LocalDate date, String body) throws Exception {
        return mvc.perform(post(url()).with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\"," + body + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void weighingsGiveGainPerDayAndUpdateCurrentWeight() throws Exception {
        log(today.minusDays(14), "\"method\":\"SCALE\",\"weightKg\":230");
        log(today.minusDays(7), "\"method\":\"TAPE\",\"weightKg\":238,\"heartGirthCm\":150,\"heightCm\":117");
        String latest = log(today, "\"method\":\"TAPE\",\"weightKg\":246,\"heightCm\":118,\"bcs\":3.0");
        log(today, "\"method\":\"ESTIMATE\",\"bcs\":3.25");

        mvc.perform(get(url()).with(as(worker)))
                .andExpect(jsonPath("$.animal.tag").value("VD-015"))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].bcs").value(3.25))
                .andExpect(jsonPath("$.items[0].gainKg").doesNotExist())
                .andExpect(jsonPath("$.items[1].weightKg").value(246.0))
                .andExpect(jsonPath("$.items[1].gainKg").value(8.0))
                .andExpect(jsonPath("$.items[1].adgKg").value(1.14))
                .andExpect(jsonPath("$.items[2].heartGirthCm").value(150.0))
                .andExpect(jsonPath("$.latest.weightKg").value(246.0))
                .andExpect(jsonPath("$.latest.heightCm").value(118.0))
                .andExpect(jsonPath("$.latest.bcs").value(3.25))
                .andExpect(jsonPath("$.adg90dKg").value(1.14));

        mvc.perform(get("/api/v1/farms/" + farmId + "/animals/" + ganga).with(as(owner)))
                .andExpect(jsonPath("$.currentWeightKg").value(246.0));

        long id = json.readTree(latest).get("id").asLong();
        mvc.perform(delete("/api/v1/farms/" + farmId + "/measurements/" + id).with(as(worker))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/farms/" + farmId + "/measurements/" + id).with(as(owner))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/farms/" + farmId + "/animals/" + ganga).with(as(owner)))
                .andExpect(jsonPath("$.currentWeightKg").value(238.0));
    }

    @Test
    void needsAValueAndQuarterStepBcs() throws Exception {
        mvc.perform(post(url()).with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\",\"method\":\"SCALE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Enter a weight, height or body condition score."));
        mvc.perform(post(url()).with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\",\"method\":\"ESTIMATE\",\"bcs\":3.1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url()).with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\",\"method\":\"ESTIMATE\",\"bcs\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bcs").value("BCS is 1 to 5"));
    }
}
