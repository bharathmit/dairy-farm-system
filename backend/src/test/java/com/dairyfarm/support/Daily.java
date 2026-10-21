package com.dairyfarm.support;

import static com.dairyfarm.support.TestTokens.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.dairyfarm.farm.FarmService;
import com.dairyfarm.support.TestTokens.TestUser;
import tools.jackson.databind.json.JsonMapper;

/** Test helpers for the daily records. */
public final class Daily {

    private Daily() {
    }

    public static LocalDate today() {
        return LocalDate.now(FarmService.FARM_ZONE);
    }

    /** Creates an animal through the API and returns its id. */
    public static long animal(MockMvc mvc, JsonMapper json, TestUser owner, long farmId, String tag, String category,
                              String extra) throws Exception {
        String sex = category.equals("BULL") ? "MALE" : "FEMALE";
        String body = "{\"tag\":\"" + tag + "\",\"species\":\"COW\",\"sex\":\"" + sex + "\",\"category\":\"" + category
                + "\",\"acquisitionType\":\"PURCHASED\",\"breed\":\"HF\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
        String res = mvc.perform(post("/api/v1/farms/" + farmId + "/animals").with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    public static long contact(MockMvc mvc, JsonMapper json, TestUser user, long farmId, String name, String roles,
                               String extra) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"roles\":[" + roles + "]" + (extra.isEmpty() ? "" : "," + extra) + "}";
        String res = mvc.perform(post("/api/v1/farms/" + farmId + "/contacts").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }
}
