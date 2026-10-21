package com.dairyfarm.contact;

import static com.dairyfarm.support.TestTokens.as;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.dairyfarm.support.Api;
import com.dairyfarm.support.Daily;
import com.dairyfarm.support.IntegrationTest;
import com.dairyfarm.support.TestTokens.TestUser;

class ContactControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private long farmId;

    @BeforeEach
    void setUp() throws Exception {
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
    }

    private String contacts() {
        return "/api/v1/farms/" + farmId + "/contacts";
    }

    @Test
    void ownerAddsContactsAndFiltersByType() throws Exception {
        Daily.contact(mvc, json, owner, farmId, "Aavin society", "\"BUYER\"", "\"milkRate\":38,\"payEvery\":\"TEN_DAYS\",\"village\":\"Kandamangalam\"");
        Daily.contact(mvc, json, owner, farmId, "Dr. Kumar", "\"VET\",\"AI_TECH\"", "\"phone\":\"+91 98400 12345\"");

        mvc.perform(get(contacts()).with(as(owner)))
                .andExpect(jsonPath("$[*].name", contains("Aavin society", "Dr. Kumar")))
                .andExpect(jsonPath("$[0].milkRate").value(38.0))
                .andExpect(jsonPath("$[0].payEvery").value("TEN_DAYS"));
        mvc.perform(get(contacts()).param("role", "VET").with(as(owner)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roles", contains("VET", "AI_TECH")));

        mvc.perform(post(contacts()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"aavin SOCIETY\",\"roles\":[\"BUYER\"]}"))
                .andExpect(status().isConflict());
        mvc.perform(post(contacts()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No type\",\"roles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.roles").exists());
    }

    @Test
    void workersAddContactsButDontSeeOrChangeRates() throws Exception {
        long id = Daily.contact(mvc, json, owner, farmId, "Aavin society", "\"BUYER\"", "\"milkRate\":38");
        mvc.perform(get(contacts() + "/" + id).with(as(worker)))
                .andExpect(jsonPath("$.name").value("Aavin society"))
                .andExpect(jsonPath("$.milkRate").doesNotExist());

        mvc.perform(put(contacts() + "/" + id).with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aavin society\",\"roles\":[\"BUYER\"],\"phone\":\"12345\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(contacts() + "/" + id).with(as(owner)))
                .andExpect(jsonPath("$.phone").value("12345"))
                .andExpect(jsonPath("$.milkRate").value(38.0));

        Daily.contact(mvc, json, worker, farmId, "Dr. Kumar", "\"VET\"", "");
        mvc.perform(delete(contacts() + "/" + id).with(as(worker))).andExpect(status().isForbidden());
    }

    @Test
    void usedContactsCanOnlyBeMadeInactive() throws Exception {
        long buyer = Daily.contact(mvc, json, owner, farmId, "Aavin society", "\"BUYER\"", "\"milkRate\":38");
        long spare = Daily.contact(mvc, json, owner, farmId, "Old buyer", "\"BUYER\"", "");
        mvc.perform(put("/api/v1/farms/" + farmId + "/milk/sales").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + Daily.today() + "\",\"session\":\"DAY\",\"quantity\":50,\"soldQty\":40,\"buyerContactId\":" + buyer + "}"))
                .andExpect(status().isOk());

        mvc.perform(delete(contacts() + "/" + buyer).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This contact is used on milk sales or health records. Make it inactive instead."));
        mvc.perform(put(contacts() + "/" + buyer).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aavin society\",\"roles\":[\"BUYER\"],\"milkRate\":38,\"active\":false}"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get(contacts()).param("active", "true").with(as(owner)))
                .andExpect(jsonPath("$[*].name", contains("Old buyer")));
        mvc.perform(delete(contacts() + "/" + spare).with(as(owner))).andExpect(status().isNoContent());
    }
}
