package com.dairyfarm.breeding;

import static com.dairyfarm.support.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dairyfarm.support.Api;
import com.dairyfarm.support.Daily;
import com.dairyfarm.support.IntegrationTest;
import com.dairyfarm.support.TestTokens.TestUser;
import tools.jackson.databind.JsonNode;

class BreedingControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private long farmId;
    private long tech;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = Daily.today();
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
        tech = Daily.contact(mvc, json, owner, farmId, "Raman AI", "\"AI_TECH\"", "");
    }

    private String base() {
        return "/api/v1/farms/" + farmId;
    }

    private JsonNode call(MockHttpServletRequestBuilder req, TestUser who, String body, int expect) throws Exception {
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String res = mvc.perform(req.with(as(who))).andExpect(status().is(expect)).andReturn().getResponse().getContentAsString();
        return res.isEmpty() ? null : json.readTree(res);
    }

    private long animal(String tag, String sex, String category, String extra) throws Exception {
        String body = "{\"tag\":\"" + tag + "\",\"species\":\"COW\",\"sex\":\"" + sex + "\",\"category\":\"" + category
                + "\",\"acquisitionType\":\"PURCHASED\",\"breed\":\"HF\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
        return call(post(base() + "/animals"), owner, body, 201).get("id").asLong();
    }

    private long semen(String bull, String code, String breed, int straws, String expiry) throws Exception {
        return call(post(base() + "/semen"), owner, "{\"bullName\":\"" + bull + "\",\"bullCode\":\"" + code + "\",\"breed\":\""
                + breed + "\",\"costPerStraw\":450,\"straws\":" + straws + ",\"expiryDate\":\"" + expiry + "\"}", 201).get("id").asLong();
    }

    private JsonNode service(long cow, LocalDate date, long semenId, String extra) throws Exception {
        return call(post(base() + "/breeding/services"), owner, "{\"animalId\":" + cow + ",\"date\":\"" + date
                + "\",\"time\":\"18:15\",\"method\":\"AI\",\"semenItemId\":" + semenId + ",\"straws\":1,\"technicianContactId\":" + tech
                + ",\"technicianFee\":300" + extra + "}", 201);
    }

    @Test
    void serviceUsesStrawsAndCalvingCreatesTheCalf() throws Exception {
        long ponni = animal("VD-002", "FEMALE", "MILKING", "\"name\":\"Ponni\",\"lastCalvingDate\":\"" + today.minusDays(300)
                + "\",\"lactationNumber\":2");
        long bullA = semen("Bull A", "JY-0456", "Jersey", 12, today.plusYears(1).toString());

        JsonNode heat = call(post(base() + "/breeding/heats"), worker, "{\"animalId\":" + ponni + ",\"date\":\"" + today.minusDays(280)
                + "\",\"time\":\"06:15\",\"signs\":[\"MOUNTING\",\"MUCUS\",\"RESTLESS\"],\"intensity\":\"STRONG\"}", 201);
        assertThat(heat.get("nextExpected").asString()).isEqualTo(today.minusDays(259).toString());
        assertThat(heat.get("heat").get("signs").size()).isEqualTo(3);
        long heatId = heat.get("heat").get("id").asLong();

        JsonNode svc = service(ponni, today.minusDays(280), bullA, ",\"heatId\":" + heatId);
        long svcId = svc.get("id").asLong();
        assertThat(svc.get("semen").get("bullCode").asString()).isEqualTo("JY-0456");
        assertThat(svc.get("semenCost").asDouble()).isEqualTo(450.0);
        assertThat(svc.get("totalCost").asDouble()).isEqualTo(750.0);
        assertThat(svc.get("expectedDueDate").asString()).isEqualTo(today.plusDays(3).toString());
        assertThat(svc.get("technician").get("name").asString()).isEqualTo("Raman AI");
        assertThat(svc.get("lactationNumber").asInt()).isEqualTo(2);
        assertThat(call(get(base() + "/semen"), owner, null, 200).get("totals").get("straws").asInt()).isEqualTo(11);
        assertThat(call(get(base() + "/breeding/heats?animalId=" + ponni), owner, null, 200).get(0).get("serviceId").asLong())
                .isEqualTo(svcId);

        // Worker sees no money; a worker's edit keeps the saved fee
        JsonNode asWorker = call(get(base() + "/breeding/services/" + svcId), worker, null, 200);
        assertThat(asWorker.get("technicianFee")).isNull();
        call(put(base() + "/breeding/services/" + svcId), worker, "{\"animalId\":" + ponni + ",\"date\":\"" + today.minusDays(280)
                + "\",\"method\":\"AI\",\"semenItemId\":" + bullA + ",\"straws\":2,\"technicianContactId\":" + tech + "}", 200);
        JsonNode edited = call(get(base() + "/breeding/services/" + svcId), owner, null, 200);
        assertThat(edited.get("technicianFee").asDouble()).isEqualTo(300.0);
        assertThat(edited.get("straws").asInt()).isEqualTo(2);
        assertThat(call(get(base() + "/semen"), owner, null, 200).get("totals").get("straws").asInt()).isEqualTo(10);

        // Pregnancy check → pregnant, due in 3 days → calving due soon
        call(post(base() + "/breeding/services/" + svcId + "/pregnancy-check"), owner,
                "{\"date\":\"" + today.minusDays(235) + "\",\"method\":\"ULTRASOUND\",\"result\":\"PREGNANT\",\"cost\":350}", 200);
        JsonNode overview = call(get(base() + "/breeding/overview"), owner, null, 200);
        assertThat(overview.get("groups").get("dueIn7").get(0).get("animal").get("id").asLong()).isEqualTo(ponni);
        assertThat(overview.get("groups").get("dueIn7").get(0).get("suggestion").get("action").asString()).isEqualTo("CALVING_DUE");
        assertThat(overview.get("calendar").toString()).contains("CALVING");

        // Calving: calf record, dam starts lactation 3, retained placenta → health follow-up
        JsonNode calved = call(post(base() + "/breeding/calvings"), worker, "{\"damId\":" + ponni + ",\"date\":\"" + today
                + "\",\"time\":\"04:30\",\"outcome\":\"LIVE\",\"ease\":\"EASY\",\"calves\":[{\"sex\":\"FEMALE\",\"birthWeightKg\":24,"
                + "\"tag\":\"VD-021\"}],\"retainedPlacenta\":true}", 201);
        assertThat(calved.get("record").get("id").asLong()).isEqualTo(svcId);
        assertThat(calved.get("record").get("calving").get("colostrumDueBy")).isNotNull();
        long calf = calved.get("calves").get(0).get("id").asLong();
        JsonNode calfDetail = call(get(base() + "/animals/" + calf), owner, null, 200);
        assertThat(calfDetail.get("category").asString()).isEqualTo("CALF");
        assertThat(calfDetail.get("breed").asString()).isEqualTo("HF × Jersey");
        assertThat(calfDetail.get("dam").get("id").asLong()).isEqualTo(ponni);
        assertThat(calfDetail.get("sire").get("tag").asString()).isEqualTo("JY-0456");
        JsonNode dam = call(get(base() + "/animals/" + ponni), owner, null, 200);
        assertThat(dam.get("lastCalvingDate").asString()).isEqualTo(today.toString());
        assertThat(dam.get("lactationNumber").asInt()).isEqualTo(3);
        assertThat(call(get(base() + "/health/records?animalId=" + ponni), owner, null, 200).get("items").get(0)
                .get("diagnosis").asString()).isEqualTo("Retained placenta");

        // Duplicate calf tag, deleting a calved service
        call(post(base() + "/breeding/calvings"), owner, "{\"damId\":" + ponni + ",\"date\":\"" + today
                + "\",\"outcome\":\"LIVE\",\"calves\":[{\"sex\":\"MALE\",\"tag\":\"VD-021\"}]}", 400);
        call(delete(base() + "/breeding/services/" + svcId), owner, null, 409);
        call(put(base() + "/breeding/services/" + svcId + "/colostrum"), worker, "{\"givenAt\":\"05:45\",\"litres\":2}", 200);

        // Family and next tag
        JsonNode fam = call(get(base() + "/animals/" + ponni + "/family"), owner, null, 200);
        assertThat(fam.get("offspring").get(0).get("animal").get("tag").asString()).isEqualTo("VD-021");
        assertThat(fam.get("offspring").get(0).get("ease").asString()).isEqualTo("EASY");
        assertThat(call(get(base() + "/animals/next-tag"), owner, null, 200).get("tag").asString()).isEqualTo("VD-022");
        assertThat(call(get(base() + "/animals/next-tag?prefix=GR-"), owner, null, 200).get("tag").asString()).isEqualTo("GR-001");

        // Calf list sees her, colostrum given via the calving record
        JsonNode calves = call(get(base() + "/calves"), owner, null, 200);
        assertThat(calves.get("calves").get(0).get("colostrum").get("given").asBoolean()).isTrue();
        assertThat(calves.get("calves").get(0).get("dam").get("id").asLong()).isEqualTo(ponni);
    }

    @Test
    void openCowHistoryShowsFailedServicesAndSuggestsSelling() throws Exception {
        long malar = animal("VD-011", "FEMALE", "MILKING", "\"name\":\"Malar\",\"lastCalvingDate\":\"" + today.minusDays(160)
                + "\",\"lactationNumber\":1");
        long bull = semen("Bull B", "HF-1234", "HF", 10, today.plusYears(1).toString());
        long s1 = service(malar, today.minusDays(130), bull, "").get("id").asLong();
        call(post(base() + "/breeding/heats"), owner, "{\"animalId\":" + malar + ",\"date\":\"" + today.minusDays(109)
                + "\",\"intensity\":\"MODERATE\"}", 201);
        long s2 = service(malar, today.minusDays(109), bull, "").get("id").asLong();
        call(post(base() + "/breeding/services/" + s2 + "/pregnancy-check"), owner,
                "{\"date\":\"" + today.minusDays(70) + "\",\"method\":\"BLOOD\",\"result\":\"NOT_PREGNANT\",\"cost\":350}", 200);

        JsonNode h = call(get(base() + "/breeding/animals/" + malar + "/history"), owner, null, 200);
        JsonNode st = h.get("status");
        assertThat(st.get("status").asString()).isEqualTo("OPEN");
        assertThat(st.get("daysOpen").asInt()).isEqualTo(160);
        assertThat(st.get("failedServices").asInt()).isEqualTo(2);
        assertThat(st.get("spendThisLactation").asDouble()).isEqualTo(1850.0);
        assertThat(st.get("suggestion").get("action").asString()).isEqualTo("CONSIDER_SELLING");
        assertThat(h.get("current").get("events").toString()).contains("\"SERVICE\"", "\"PD\"", "\"HEAT\"", "\"CALVING\"", "failed");
        assertThat(h.get("current").get("events").get(0).get("date").asString()).isGreaterThanOrEqualTo(today.minusDays(70).toString());

        JsonNode asWorker = call(get(base() + "/breeding/animals/" + malar + "/history"), worker, null, 200);
        assertThat(asWorker.get("status").get("spendThisLactation")).isNull();

        // Heifer of service age shows as first service due and on the heat watch
        animal("VD-015", "FEMALE", "HEIFER", "\"name\":\"Ganga\",\"dateOfBirth\":\"" + today.minusMonths(15) + "\"");
        JsonNode overview = call(get(base() + "/breeding/overview"), owner, null, 200);
        assertThat(overview.get("breedable").asInt()).isEqualTo(2);
        assertThat(overview.get("groups").get("open").toString()).contains("CONSIDER_SELLING", "FIRST_SERVICE_DUE");
        JsonNode watch = call(get(base() + "/breeding/heat-watch"), owner, null, 200);
        assertThat(watch.toString()).contains("\"LOW\"");

        // Services can't be before calving, semen must not be expired or over-used
        call(post(base() + "/breeding/services"), owner, "{\"animalId\":" + malar + ",\"date\":\"" + today.minusDays(200)
                + "\",\"method\":\"AI\",\"semenItemId\":" + bull + "}", 400);
        long old = semen("Old Bull", "OB-1", "Gir", 2, today.minusDays(10).toString());
        call(post(base() + "/breeding/services"), owner, "{\"animalId\":" + malar + ",\"date\":\"" + today
                + "\",\"method\":\"AI\",\"semenItemId\":" + old + "}", 400);
        long few = semen("Few Bull", "FB-1", "Gir", 1, today.plusDays(100).toString());
        call(post(base() + "/breeding/services"), owner, "{\"animalId\":" + malar + ",\"date\":\"" + today
                + "\",\"method\":\"AI\",\"semenItemId\":" + few + ",\"straws\":2}", 400);
        assertThat(call(get(base() + "/semen/" + few), owner, null, 200).get("straws").asInt()).isEqualTo(1);
        call(delete(base() + "/semen/" + bull), owner, null, 409);
        call(delete(base() + "/breeding/services/" + s1), worker, null, 403);
        call(delete(base() + "/breeding/services/" + s1), owner, null, 204);
        assertThat(call(get(base() + "/semen/" + bull), owner, null, 200).get("straws").asInt()).isEqualTo(9);
    }

    @Test
    void naturalServiceWithOutsideBullAndOwnerFee() throws Exception {
        long cow = animal("VD-005", "FEMALE", "MILKING", "\"lastCalvingDate\":\"" + today.minusDays(90) + "\"");
        long neighbour = Daily.contact(mvc, json, owner, farmId, "Neighbour", "\"OTHER\"", "");
        JsonNode s = call(post(base() + "/breeding/services"), owner, "{\"animalId\":" + cow + ",\"date\":\"" + today.minusDays(2)
                + "\",\"method\":\"NATURAL\",\"sireTag\":\"Kangayam bull\",\"bullOwnerContactId\":" + neighbour
                + ",\"bullFee\":500,\"feePaid\":true}", 201);
        assertThat(s.get("bullOwner").get("name").asString()).isEqualTo("Neighbour");
        assertThat(s.get("sireTag").asString()).isEqualTo("Kangayam bull");
        assertThat(s.get("totalCost").asDouble()).isEqualTo(500.0);
        assertThat(s.get("feePaid").asBoolean()).isTrue();
        assertThat(s.get("semen")).isNull();
        // a buyer can't be the AI technician
        long buyer = Daily.contact(mvc, json, owner, farmId, "Dairy society", "\"BUYER\"", "");
        call(post(base() + "/breeding/services"), owner, "{\"animalId\":" + cow + ",\"date\":\"" + today
                + "\",\"method\":\"NATURAL\",\"technicianContactId\":" + buyer + "}", 400);
        // a male can't be served
        long bull = animal("VD-B1", "MALE", "BULL", "");
        call(post(base() + "/breeding/heats"), owner, "{\"animalId\":" + bull + ",\"date\":\"" + today + "\",\"intensity\":\"MILD\"}", 400);
    }

    @Test
    void calvesListFeedingAndGroupSheet() throws Exception {
        long f1 = animal("VD-019", "FEMALE", "CALF", "\"dateOfBirth\":\"" + today.minusDays(23) + "\"");
        long f2 = animal("VD-017", "FEMALE", "CALF", "\"dateOfBirth\":\"" + today.minusDays(100) + "\"");
        long m1 = animal("VD-020", "MALE", "CALF", "\"dateOfBirth\":\"" + today.minusDays(85) + "\"");

        JsonNode list = call(get(base() + "/calves?sex=FEMALE&maxDays=90"), owner, null, 200);
        assertThat(list.get("counts").get("all").asInt()).isEqualTo(3);
        assertThat(list.get("counts").get("female").asInt()).isEqualTo(2);
        assertThat(list.get("counts").get("male").asInt()).isEqualTo(1);
        assertThat(list.get("counts").get("shown").asInt()).isEqualTo(1);
        assertThat(list.get("calves").get(0).get("plan").get("milkLitres").asDouble()).isEqualTo(3.0);
        assertThat(call(get(base() + "/calves?minDays=80"), owner, null, 200).get("counts").get("shown").asInt()).isEqualTo(2);

        JsonNode day = call(put(base() + "/calves/" + f1 + "/feeding/" + today), worker,
                "{\"milkAm\":1.5,\"starterKg\":0.2,\"notes\":\"Loose dung\"}", 200);
        assertThat(day.get("record").get("milkAm").asDouble()).isEqualTo(1.5);
        assertThat(day.get("plan").get("fromDay").asInt()).isEqualTo(15);

        JsonNode sheet = call(put(base() + "/calves/feeding-sheet"), worker, "{\"date\":\"" + today + "\",\"session\":\"PM\","
                + "\"entries\":[{\"animalId\":" + f1 + ",\"litres\":1.5},{\"animalId\":" + m1 + ",\"litres\":1.0,\"starterKg\":0.8}]}", 200);
        assertThat(sheet.get("calves").size()).isEqualTo(2);
        JsonNode get = call(get(base() + "/calves/feeding-sheet?session=PM&sex=FEMALE&maxDays=90"), owner, null, 200);
        assertThat(get.get("calves").get(0).get("litres").asDouble()).isEqualTo(1.5);
        assertThat(get.get("calves").get(0).get("starterKg").asDouble()).isEqualTo(0.2);
        JsonNode after = call(get(base() + "/calves"), owner, null, 200);
        assertThat(after.get("milkTodayLitres").asDouble()).isEqualTo(4.0);
        String male = after.get("calves").toString();
        assertThat(male).contains("sell window");
        JsonNode one = call(get(base() + "/calves/" + f1 + "/feeding?date=" + today), owner, null, 200);
        assertThat(one.get("record").get("milkPm").asDouble()).isEqualTo(1.5);
        assertThat(one.get("record").get("notes").asString()).isEqualTo("Loose dung");
        // a non-calf can't be fed here
        long cow = animal("VD-030", "FEMALE", "MILKING", "");
        call(put(base() + "/calves/" + cow + "/feeding/" + today), owner, "{\"milkAm\":1}", 400);
        assertThat(f2).isPositive();
    }

    @Test
    void mateCheckFlagsFatherDaughter() throws Exception {
        long bull = animal("VD-B2", "MALE", "BULL", "");
        long heifer = animal("VD-040", "FEMALE", "HEIFER", "\"sireId\":" + bull);
        long other = semen("Bull C", "GR-0789", "Gir", 5, today.plusYears(1).toString());
        JsonNode high = call(get(base() + "/animals/" + heifer + "/mate-check?sireAnimalId=" + bull), owner, null, 200);
        assertThat(high.get("risk").asString()).isEqualTo("HIGH");
        assertThat(high.get("relation").asString()).isEqualTo("FATHER_DAUGHTER");
        JsonNode none = call(get(base() + "/animals/" + heifer + "/mate-check?semenItemId=" + other), owner, null, 200);
        assertThat(none.get("risk").asString()).isEqualTo("NONE");
        assertThat(none.get("knownPct").asInt()).isEqualTo(17);
        call(get(base() + "/animals/" + heifer + "/mate-check"), owner, null, 400);
    }

    @Test
    void settingsAreOwnerOnlyAndValidated() throws Exception {
        JsonNode s = call(get(base() + "/breeding/settings"), worker, null, 200);
        assertThat(s.get("gestationDays").get("COW").asInt()).isEqualTo(283);
        assertThat(s.get("calfTaper").size()).isEqualTo(5);
        call(put(base() + "/breeding/settings"), worker, "{\"sellDaysOpen\":180}", 403);
        call(put(base() + "/breeding/settings"), owner, "{\"maleSellFromDay\":90,\"maleSellToDay\":30}", 400);
        JsonNode saved = call(put(base() + "/breeding/settings"), owner, "{\"sellDaysOpen\":180,\"gestationDays\":{\"COW\":280}}", 200);
        assertThat(saved.get("sellDaysOpen").asInt()).isEqualTo(180);
        assertThat(saved.get("gestationDays").get("COW").asInt()).isEqualTo(280);
        assertThat(saved.get("gestationDays").get("BUFFALO").asInt()).isEqualTo(310);
        assertThat(call(get(base() + "/breeding/settings"), owner, null, 200).get("heatCycleDays").asInt()).isEqualTo(21);
    }
}
