package com.dairyfarm.group;

import static com.dairyfarm.support.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
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
import com.dairyfarm.support.IntegrationTest;
import com.dairyfarm.support.TestTokens.TestUser;

class GroupControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private long farmId;
    private long murugan;

    @BeforeEach
    void setUp() throws Exception {
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
        murugan = jdbc.queryForObject("select id from farm_member where user_id = ?", Long.class, worker.id());
    }

    private String groups() {
        return "/api/v1/farms/" + farmId + "/groups";
    }

    private long animal(String tag, String species, String status) {
        return jdbc.queryForObject("""
                insert into animal (farm_id, tag, species, sex, category, acquisition_type, status, exit_date)
                values (?, ?, ?, 'FEMALE', 'MILKING', 'PURCHASED', ?, case when ? = 'ACTIVE' then null else current_date end)
                returning id""", Long.class, farmId, tag, species, status, status);
    }

    private long createGroup(String body) throws Exception {
        String res = mvc.perform(post(groups()).with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    @Test
    void createsAGroupWithManagerAndColour() throws Exception {
        mvc.perform(post(groups()).with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Milking shed A","species":"COW","location":"Shed A","purpose":"MAINTENANCE",
                         "managerMemberId":%d,"colour":"#1f5c3a"}""".formatted(murugan)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.managerName").value("Murugan"))
                .andExpect(jsonPath("$.colour").value("#1F5C3A"))
                .andExpect(jsonPath("$.animals", empty()));

        mvc.perform(post(groups()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"milking shed a\",\"species\":\"COW\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post(groups()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Calves\",\"species\":\"COW\",\"colour\":\"green\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.colour").exists());
        mvc.perform(post(groups()).with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Calves\",\"species\":\"COW\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void setsMembersMovingAnimalsBetweenGroups() throws Exception {
        long shed = createGroup("{\"name\":\"Milking shed A\",\"species\":\"COW\"}");
        long dry = createGroup("{\"name\":\"Dry cows\",\"species\":\"COW\",\"purpose\":\"GESTATION\"}");
        long a1 = animal("VD-001", "COW", "ACTIVE");
        long a2 = animal("VD-002", "COW", "ACTIVE");
        long a7 = animal("VD-007", "COW", "ACTIVE");

        mvc.perform(put(groups() + "/" + shed + "/animals").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalIds\":[" + a1 + "," + a2 + "," + a7 + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.animals[*].tag", contains("VD-001", "VD-002", "VD-007")));

        mvc.perform(put(groups() + "/" + dry + "/animals").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalIds\":[" + a7 + "]}"))
                .andExpect(jsonPath("$.animals[*].tag", contains("VD-007")));

        mvc.perform(get(groups()).with(as(worker)))
                .andExpect(jsonPath("$[0].name").value("Dry cows"))
                .andExpect(jsonPath("$[0].animalCount").value(1))
                .andExpect(jsonPath("$[1].animalCount").value(2));

        mvc.perform(put(groups() + "/" + shed + "/animals").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalIds\":[" + a1 + "]}"))
                .andExpect(jsonPath("$.animals[*].tag", contains("VD-001")));
        assertThat(jdbc.queryForObject("select group_id from animal where id = ?", Long.class, a2)).isNull();
    }

    @Test
    void onlyActiveAnimalsOfTheSameSpecies() throws Exception {
        long shed = createGroup("{\"name\":\"Milking shed A\",\"species\":\"COW\"}");
        long goat = animal("G-01", "GOAT", "ACTIVE");
        long sold = animal("VD-009", "COW", "SOLD");

        mvc.perform(put(groups() + "/" + shed + "/animals").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalIds\":[" + goat + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("G-01 is a goat; Milking shed A is for cow only."));
        mvc.perform(put(groups() + "/" + shed + "/animals").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalIds\":[" + sold + "]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(groups() + "/" + shed + "/animals").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalIds\":[123456]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editsButSpeciesStaysAndDeleteKeepsTheAnimals() throws Exception {
        long shed = createGroup("{\"name\":\"Milking shed A\",\"species\":\"COW\"}");
        long a1 = animal("VD-001", "COW", "ACTIVE");
        mvc.perform(put(groups() + "/" + shed + "/animals").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"animalIds\":[" + a1 + "]}"));

        mvc.perform(put(groups() + "/" + shed).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Shed A\",\"species\":\"COW\",\"notes\":\"Fans fixed Oct\"}"))
                .andExpect(jsonPath("$.name").value("Shed A"))
                .andExpect(jsonPath("$.animals[0].tag").value("VD-001"));
        mvc.perform(put(groups() + "/" + shed).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Shed A\",\"species\":\"BUFFALO\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete(groups() + "/" + shed).with(as(owner))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from animal where id = ? and group_id is null", Long.class, a1))
                .isEqualTo(1);
    }

    @Test
    void removingTheManagerLeavesTheGroupWithoutOne() throws Exception {
        long shed = createGroup("{\"name\":\"Milking shed A\",\"species\":\"COW\",\"managerMemberId\":" + murugan + "}");
        jdbc.update("delete from farm_member where id = ?", murugan);
        mvc.perform(get(groups() + "/" + shed).with(as(owner)))
                .andExpect(jsonPath("$.managerMemberId").doesNotExist());
    }
}
