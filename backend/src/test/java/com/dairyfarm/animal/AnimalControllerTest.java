package com.dairyfarm.animal;

import static com.dairyfarm.support.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.dairyfarm.support.Api;
import com.dairyfarm.support.IntegrationTest;
import com.dairyfarm.support.TestTokens.TestUser;

class AnimalControllerTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private long farmId;

    @BeforeEach
    void setUp() throws Exception {
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
    }

    private String animals() {
        return "/api/v1/farms/" + farmId + "/animals";
    }

    private long create(String body) throws Exception {
        String res = mvc.perform(post(animals()).with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private static String cow(String tag, String sex, String category, String extra) {
        return "{\"tag\":\"" + tag + "\",\"species\":\"COW\",\"sex\":\"" + sex + "\",\"category\":\"" + category
                + "\",\"acquisitionType\":\"PURCHASED\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private long group(String name, String species) {
        return jdbc.queryForObject("insert into animal_group (farm_id, name, species) values (?, ?, ?) returning id",
                Long.class, farmId, name, species);
    }

    @Test
    void addsAnAnimalWithAllDetails() throws Exception {
        long shed = group("Milking shed A", "COW");
        mvc.perform(post(animals()).with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"tag":"VD-001","name":"Lakshmi","species":"COW","breed":"HF","sex":"FEMALE",
                         "dateOfBirth":"2021-08-14","colour":"Black & white","category":"MILKING",
                         "groupId":%d,"damTag":"D-12","sireTag":"AI · HF-221","acquisitionType":"PURCHASED",
                         "acquisitionDate":"2023-02-10","purchasePrice":65000,"lastCalvingDate":"2026-07-28",
                         "currentWeightKg":452}""".formatted(shed)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tag").value("VD-001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.groupName").value("Milking shed A"))
                .andExpect(jsonPath("$.dam.tag").value("D-12"))
                .andExpect(jsonPath("$.dam.id").doesNotExist())
                .andExpect(jsonPath("$.sire.tag").value("AI · HF-221"))
                .andExpect(jsonPath("$.purchasePrice").value(65000))
                .andExpect(jsonPath("$.lastCalvingDate").value("2026-07-28"));

        mvc.perform(get("/api/v1/me").with(as(owner)))
                .andExpect(jsonPath("$.farms[0].animalCount").value(1));
    }

    @Test
    void tagsAreUniquePerFarmIgnoringCase() throws Exception {
        create(cow("VD-001", "FEMALE", "MILKING", ""));
        mvc.perform(post(animals()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(cow("vd-001", "FEMALE", "DRY", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Tag vd-001 is already used on this farm."));
    }

    @Test
    void checksTheFarmRules() throws Exception {
        mvc.perform(post(animals()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(cow("VD-050", "MALE", "MILKING", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Milking animals must be female."));
        mvc.perform(post(animals()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"VD-051\",\"species\":\"COW\",\"sex\":\"FEMALE\",\"category\":\"CALF\",\"acquisitionType\":\"BORN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Add the date of birth for an animal born on the farm."));
        mvc.perform(post(animals()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"species\":\"COW\",\"sex\":\"FEMALE\",\"category\":\"CALF\",\"acquisitionType\":\"PURCHASED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tag").value("Enter the ear tag number"));
        long goats = group("Goat pen", "GOAT");
        mvc.perform(post(animals()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(cow("VD-052", "FEMALE", "HEIFER", "\"groupId\":" + goats)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Goat pen is a goat group; this animal is a cow."));
    }

    @Test
    void linksParentsByTagEvenWhenTheCalfIsAddedFirst() throws Exception {
        long calf = create("{\"tag\":\"VD-019\",\"species\":\"COW\",\"sex\":\"FEMALE\",\"category\":\"CALF\","
                + "\"acquisitionType\":\"BORN\",\"dateOfBirth\":\"2026-09-28\",\"damTag\":\"VD-001\"}");
        long dam = create(cow("VD-001", "FEMALE", "MILKING", "\"name\":\"Lakshmi\""));

        mvc.perform(get(animals() + "/" + calf).with(as(owner)))
                .andExpect(jsonPath("$.dam.id").value(dam))
                .andExpect(jsonPath("$.dam.name").value("Lakshmi"));

        mvc.perform(post(animals()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"VD-020\",\"species\":\"COW\",\"sex\":\"MALE\",\"category\":\"CALF\","
                                + "\"acquisitionType\":\"BORN\",\"dateOfBirth\":\"2026-10-05\",\"sireId\":" + dam + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VD-001 is female, so it can't be the father."));

        mvc.perform(delete(animals() + "/" + dam).with(as(owner))).andExpect(status().isNoContent());
        mvc.perform(get(animals() + "/" + calf).with(as(owner)))
                .andExpect(jsonPath("$.dam.id").doesNotExist())
                .andExpect(jsonPath("$.dam.tag").value("VD-001"));
    }

    @Test
    void filtersSortsAndCounts() throws Exception {
        long shed = group("Milking shed A", "COW");
        create(cow("VD-003", "FEMALE", "MILKING", "\"name\":\"Meena\",\"groupId\":" + shed));
        create(cow("VD-001", "FEMALE", "MILKING", "\"name\":\"Lakshmi\",\"groupId\":" + shed));
        create(cow("VD-007", "FEMALE", "DRY", "\"name\":\"Rani\""));
        long sold = create(cow("VD-009", "MALE", "BULL", ""));
        mvc.perform(patch(animals() + "/" + sold + "/status").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SOLD\",\"date\":\"2026-10-01\",\"price\":40000,\"reason\":\"Age / replacement\",\"party\":\"Ravi\"}"));

        mvc.perform(get(animals()).param("status", "ACTIVE").with(as(worker)))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[*].tag", contains("VD-001", "VD-003", "VD-007")));
        mvc.perform(get(animals()).param("category", "MILKING").param("groupId", String.valueOf(shed)).with(as(worker)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].groupName").value("Milking shed A"));
        mvc.perform(get(animals()).param("noGroup", "true").param("status", "ACTIVE").with(as(worker)))
                .andExpect(jsonPath("$.items[*].tag", contains("VD-007")));
        mvc.perform(get(animals()).param("q", "lak").with(as(worker)))
                .andExpect(jsonPath("$.items[*].tag", contains("VD-001")));
        mvc.perform(get(animals()).param("sort", "newest").param("size", "2").with(as(worker)))
                .andExpect(jsonPath("$.items[*].tag", contains("VD-009", "VD-007")))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(animals()).param("sort", "weight").with(as(worker)))
                .andExpect(status().isBadRequest());

        mvc.perform(get(animals() + "/summary").with(as(worker)))
                .andExpect(jsonPath("$.active").value(3))
                .andExpect(jsonPath("$.activeByCategory.MILKING").value(2))
                .andExpect(jsonPath("$.activeByCategory.DRY").value(1))
                .andExpect(jsonPath("$.byStatus.SOLD").value(1));
    }

    @Test
    void detailsListGivesFullRecordsWithParentsAndPhotos() throws Exception {
        long dam = create(cow("VD-001", "FEMALE", "MILKING", "\"name\":\"Lakshmi\",\"notes\":\"Calm\",\"purchasePrice\":65000"));
        create("{\"tag\":\"VD-019\",\"species\":\"COW\",\"sex\":\"FEMALE\",\"category\":\"CALF\","
                + "\"acquisitionType\":\"BORN\",\"dateOfBirth\":\"2026-09-28\",\"damTag\":\"VD-001\"}");
        mvc.perform(multipart(animals() + "/" + dam + "/photos")
                .file(new MockMultipartFile("file", "cow.png", "image/png", PNG)).with(as(owner)));

        mvc.perform(get(animals() + "/details").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].notes").value("Calm"))
                .andExpect(jsonPath("$.items[0].purchasePrice").value(65000))
                .andExpect(jsonPath("$.items[0].photos[0].url").exists())
                .andExpect(jsonPath("$.items[1].dam.id").value(dam))
                .andExpect(jsonPath("$.items[1].dam.name").value("Lakshmi"));
        mvc.perform(get(animals() + "/details").with(as(worker)))
                .andExpect(jsonPath("$.items[0].purchasePrice").doesNotExist());
    }

    @Test
    void workersCanReadButNotChangeAndDontSeePrices() throws Exception {
        long id = create(cow("VD-001", "FEMALE", "MILKING", "\"purchasePrice\":65000"));
        mvc.perform(get(animals() + "/" + id).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasePrice").doesNotExist());
        mvc.perform(get(animals() + "/by-tag/vd-001").with(as(worker)))
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(post(animals()).with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content(cow("VD-002", "FEMALE", "MILKING", "")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(animals() + "/" + id).with(as(worker))).andExpect(status().isForbidden());
    }

    @Test
    void editsButSpeciesStaysFixed() throws Exception {
        long id = create(cow("VD-001", "FEMALE", "HEIFER", ""));
        mvc.perform(put(animals() + "/" + id).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(cow("VD-001", "FEMALE", "MILKING", "\"name\":\"Lakshmi\",\"lastCalvingDate\":\"2026-07-28\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("MILKING"))
                .andExpect(jsonPath("$.name").value("Lakshmi"));
        mvc.perform(put(animals() + "/" + id).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(cow("VD-001", "FEMALE", "MILKING", "").replace("COW", "BUFFALO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Species can't be changed after an animal is added."));
    }

    @Test
    void recordsASaleAndBringsTheAnimalBack() throws Exception {
        long shed = group("Milking shed A", "COW");
        long id = create(cow("VD-004", "FEMALE", "MILKING", "\"groupId\":" + shed));
        mvc.perform(patch(animals() + "/" + id + "/status").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SOLD\",\"date\":\"2026-10-01\",\"price\":42000,\"reason\":\"Low milk yield (cull)\",\"party\":\"Ravi, Tindivanam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.exitPrice").value(42000))
                .andExpect(jsonPath("$.exitParty").value("Ravi, Tindivanam"))
                .andExpect(jsonPath("$.groupId").doesNotExist());
        mvc.perform(patch(animals() + "/" + id + "/group").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + shed + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(animals() + "/" + id + "/status").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.exitDate").doesNotExist());
    }

    @Test
    void bulkDeleteOnlyTouchesThisFarm() throws Exception {
        long a = create(cow("VD-001", "FEMALE", "MILKING", ""));
        long b = create(cow("VD-002", "FEMALE", "MILKING", ""));
        mvc.perform(post(animals() + "/bulk-delete").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + a + "," + b + ",999999]}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(animals() + "/bulk-delete").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + a + "," + b + "]}"))
                .andExpect(jsonPath("$.deleted").value(2));
    }

    @Test
    void photosUpToThreeImagesOnly() throws Exception {
        long id = create(cow("VD-001", "FEMALE", "MILKING", ""));
        var png = new MockMultipartFile("file", "cow.png", "image/png", PNG);
        String res = mvc.perform(multipart(animals() + "/" + id + "/photos").file(png).with(as(worker)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos", hasSize(1)))
                .andExpect(jsonPath("$.photos[0].url").exists())
                .andReturn().getResponse().getContentAsString();
        String photoId = json.readTree(res).get("photos").get(0).get("id").asText();
        assertThat(photoStorage.files).hasSize(1);

        mvc.perform(multipart(animals() + "/" + id + "/photos").file(png).with(as(owner))).andExpect(status().isCreated());
        mvc.perform(multipart(animals() + "/" + id + "/photos").file(png).with(as(owner))).andExpect(status().isCreated());
        mvc.perform(multipart(animals() + "/" + id + "/photos").file(png).with(as(owner)))
                .andExpect(status().isConflict());

        var fake = new MockMultipartFile("file", "cow.png", "image/png", "not really an image".getBytes());
        long other = create(cow("VD-002", "FEMALE", "MILKING", ""));
        mvc.perform(multipart(animals() + "/" + other + "/photos").file(fake).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Use a JPG, PNG or WebP photo."));

        mvc.perform(get(animals()).with(as(owner)))
                .andExpect(jsonPath("$.items[0].photoUrl").exists());

        mvc.perform(delete(animals() + "/" + id + "/photos/" + photoId).with(as(owner)))
                .andExpect(jsonPath("$.photos", hasSize(2)));
        assertThat(photoStorage.files).hasSize(2);

        mvc.perform(delete(animals() + "/" + id).with(as(owner))).andExpect(status().isNoContent());
        assertThat(photoStorage.files).isEmpty();
    }
}
