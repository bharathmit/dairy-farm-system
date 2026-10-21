package com.dairyfarm.record;

import static com.dairyfarm.support.TestTokens.as;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

class HealthControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private long farmId;
    private long lakshmi;
    private long ponni;
    private long vet;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = Daily.today();
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
        lakshmi = Daily.animal(mvc, json, owner, farmId, "VD-001", "MILKING", "\"name\":\"Lakshmi\"");
        ponni = Daily.animal(mvc, json, owner, farmId, "VD-002", "MILKING", "\"name\":\"Ponni\"");
        vet = Daily.contact(mvc, json, owner, farmId, "Dr. Kumar", "\"VET\"", "");
    }

    private String health() {
        return "/api/v1/farms/" + farmId + "/health";
    }

    private String treat(TestUser who, long animal, LocalDate date, String extra) throws Exception {
        return mvc.perform(post(health() + "/records").with(as(who)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalId\":" + animal + ",\"date\":\"" + date + "\",\"type\":\"TREATMENT\"" + extra + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void treatmentSetsWithdrawalEndAtEveningMilking() throws Exception {
        String body = treat(owner, ponni, today, ",\"symptoms\":\"Clots in milk\",\"diagnosis\":\"Clinical mastitis\","
                + "\"medicine\":\"Ceftriaxone\",\"dose\":\"10 ml\",\"route\":\"Intramuscular\",\"vetContactId\":" + vet
                + ",\"cost\":650,\"milkWithdrawalDays\":3,\"meatWithdrawalDays\":10,"
                + "\"nextActionDate\":\"" + today.plusDays(2) + "\",\"nextAction\":\"Recheck udder\"");
        var r = json.readTree(body);
        org.assertj.core.api.Assertions.assertThat(r.get("milkWithdrawalUntil").asString())
                .isEqualTo(today.plusDays(3).atTime(18, 0).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toString());
        org.assertj.core.api.Assertions.assertThat(r.get("meatWithdrawalUntil").asString()).isEqualTo(today.plusDays(10).toString());
        org.assertj.core.api.Assertions.assertThat(r.get("vet").get("name").asString()).isEqualTo("Dr. Kumar");
        org.assertj.core.api.Assertions.assertThat(r.get("diagnosis").asString()).isEqualTo("Clinical mastitis");

        // Worker sees the record but not the cost, and editing keeps the cost
        long id = r.get("id").asLong();
        mvc.perform(get(health() + "/records/" + id).with(as(worker)))
                .andExpect(jsonPath("$.cost").doesNotExist())
                .andExpect(jsonPath("$.medicine").value("Ceftriaxone"));
        mvc.perform(put(health() + "/records/" + id).with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalId\":" + ponni + ",\"date\":\"" + today + "\",\"type\":\"TREATMENT\",\"milkWithdrawalDays\":3,"
                                + "\"nextActionDate\":\"" + today.plusDays(2) + "\",\"notes\":\"Better\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(health() + "/records/" + id).with(as(owner)))
                .andExpect(jsonPath("$.cost").value(650.0))
                .andExpect(jsonPath("$.notes").value("Better"))
                .andExpect(jsonPath("$.nextAction").value("Follow-up"));

        mvc.perform(get(health() + "/withdrawals").with(as(owner)))
                .andExpect(jsonPath("$.active", hasSize(1)))
                .andExpect(jsonPath("$.active[0].record.tag").value("VD-002"))
                .andExpect(jsonPath("$.ended", hasSize(0)));
    }

    @Test
    void vetMustBeAVetContact() throws Exception {
        long buyer = Daily.contact(mvc, json, owner, farmId, "Aavin", "\"BUYER\"", "");
        mvc.perform(post(health() + "/records").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalId\":" + ponni + ",\"date\":\"" + today + "\",\"type\":\"ILLNESS\",\"vetContactId\":" + buyer + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Aavin isn't saved as a vet. Edit the contact to add it."));
        mvc.perform(post(health() + "/records").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalId\":" + ponni + ",\"date\":\"" + today + "\",\"type\":\"ILLNESS\","
                                + "\"nextActionDate\":\"" + today.minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchOverviewFollowUpsAndDiscardedMilk() throws Exception {
        String batch = mvc.perform(post(health() + "/records/batch").with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalIds\":[" + lakshmi + "," + ponni + "],\"date\":\"" + today + "\",\"type\":\"VACCINATION\","
                                + "\"medicine\":\"FMD vaccine\",\"dose\":\"2 ml\",\"nextActionDate\":\"" + today.plusDays(3)
                                + "\",\"nextAction\":\"FMD booster\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.records", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        long first = json.readTree(batch).get("records").get(0).get("id").asLong();

        // Lakshmi treated 2 days ago with 5 days' withdrawal; her milk since then is discarded
        jdbc.update("insert into production_record (farm_id, record_date, session, animal_id, quantity) values (?, ?, 'PM', ?, 9)",
                farmId, today.minusDays(1), lakshmi);
        treat(worker, lakshmi, today.minusDays(2), ",\"milkWithdrawalDays\":5");
        // An old treatment that ended 3 days ago
        treat(worker, ponni, today.minusDays(10), ",\"milkWithdrawalDays\":7");

        mvc.perform(get(health() + "/overview").with(as(worker)))
                .andExpect(jsonPath("$.inWithdrawal").value(1))
                .andExpect(jsonPath("$.openFollowUps").value(2))
                .andExpect(jsonPath("$.dueFollowUps", hasSize(2)))
                .andExpect(jsonPath("$.dueFollowUps[0].nextAction").value("FMD booster"))
                .andExpect(jsonPath("$.recent", hasSize(4)))
                .andExpect(jsonPath("$.recent[0].type").value("VACCINATION"));

        mvc.perform(put(health() + "/records/" + first + "/follow-up").with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"done\":true}"))
                .andExpect(jsonPath("$.nextActionDone").value(true));
        mvc.perform(get(health() + "/overview").with(as(worker)))
                .andExpect(jsonPath("$.openFollowUps").value(1));

        mvc.perform(get(health() + "/withdrawals").with(as(worker)))
                .andExpect(jsonPath("$.active", hasSize(1)))
                .andExpect(jsonPath("$.active[0].record.tag").value("VD-001"))
                .andExpect(jsonPath("$.active[0].discardedLitres").value(9.0))
                .andExpect(jsonPath("$.ended", hasSize(1)))
                .andExpect(jsonPath("$.ended[0].record.tag").value("VD-002"));

        mvc.perform(get(health() + "/records").param("type", "VACCINATION").with(as(worker)))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get(health() + "/records").param("animalId", String.valueOf(lakshmi)).with(as(worker)))
                .andExpect(jsonPath("$.totalItems").value(2));

        mvc.perform(delete(health() + "/records/" + first).with(as(worker))).andExpect(status().isForbidden());
        mvc.perform(delete(health() + "/records/" + first).with(as(owner))).andExpect(status().isNoContent());
    }
}
