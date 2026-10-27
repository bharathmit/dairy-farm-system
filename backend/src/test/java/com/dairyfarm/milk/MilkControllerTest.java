package com.dairyfarm.milk;

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

class MilkControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private long farmId;
    private long lakshmi;
    private long ponni;
    private long heifer;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = Daily.today();
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
        lakshmi = Daily.animal(mvc, json, owner, farmId, "VD-001", "MILKING", "\"name\":\"Lakshmi\"");
        ponni = Daily.animal(mvc, json, owner, farmId, "VD-002", "MILKING",
                "\"name\":\"Ponni\",\"lastCalvingDate\":\"" + today.minusDays(83) + "\"");
        heifer = Daily.animal(mvc, json, owner, farmId, "VD-015", "HEIFER", "\"name\":\"Ganga\"");
    }

    private String milk() {
        return "/api/v1/farms/" + farmId + "/milk";
    }

    private void sheet(TestUser who, String session, String entries) throws Exception {
        mvc.perform(put(milk() + "/sheet").with(as(who)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\",\"session\":\"" + session + "\",\"entries\":[" + entries + "]}"))
                .andExpect(status().isOk());
    }

    @Test
    void herdSheetSavesSkipsAndPrefillsFromLastRecord() throws Exception {
        jdbc.update("insert into production_record (farm_id, record_date, session, animal_id, quantity) values (?, ?, 'AM', ?, 10.5)",
                farmId, today.minusDays(1), lakshmi);

        mvc.perform(get(milk() + "/sheet").param("date", today.toString()).param("session", "AM").with(as(worker)))
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.cows", hasSize(2)))
                .andExpect(jsonPath("$.cows[0].tag").value("VD-001"))
                .andExpect(jsonPath("$.cows[0].litres").doesNotExist())
                .andExpect(jsonPath("$.cows[0].lastLitres").value(10.5))
                .andExpect(jsonPath("$.cows[0].usualLitres").value(10.5));

        sheet(worker, "AM", "{\"animalId\":" + lakshmi + ",\"litres\":9.5},{\"animalId\":" + ponni + ",\"litres\":8}");
        sheet(worker, "AM", "{\"animalId\":" + lakshmi + ",\"litres\":9.5},{\"animalId\":" + ponni + ",\"litres\":null}");
        sheet(worker, "PM", "{\"animalId\":" + lakshmi + ",\"litres\":9}");

        mvc.perform(get(milk() + "/sheet").param("date", today.toString()).param("session", "AM").with(as(worker)))
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.cows[0].litres").value(9.5))
                .andExpect(jsonPath("$.cows[1].litres").doesNotExist())
                .andExpect(jsonPath("$.sessionLitres").value(9.5))
                .andExpect(jsonPath("$.otherSessionLitres").value(9.0))
                .andExpect(jsonPath("$.dayLitres").value(18.5));

        mvc.perform(get(milk() + "/day").param("date", today.toString()).with(as(owner)))
                .andExpect(jsonPath("$.am.litres").value(9.5))
                .andExpect(jsonPath("$.am.cows").value(1))
                .andExpect(jsonPath("$.totalLitres").value(18.5))
                .andExpect(jsonPath("$.yesterday.amLitres").value(10.5))
                .andExpect(jsonPath("$.change.amPct").value(-9.5))
                .andExpect(jsonPath("$.change.pmPct").doesNotExist())
                .andExpect(jsonPath("$.milkingCows").value(2))
                .andExpect(jsonPath("$.cows", hasSize(2)));

        mvc.perform(get(milk() + "/trend").param("from", today.minusDays(6).toString()).param("to", today.toString()).with(as(owner)))
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[5].amLitres").value(10.5))
                .andExpect(jsonPath("$[6].totalLitres").value(18.5));
    }

    @Test
    void onlyMilkingCowsAndNoFutureDates() throws Exception {
        mvc.perform(put(milk() + "/sheet").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\",\"session\":\"AM\",\"entries\":[{\"animalId\":" + heifer + ",\"litres\":3}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VD-015 isn't a milking cow. Change her category to Milking first."));
        mvc.perform(put(milk() + "/sheet").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today.plusDays(1) + "\",\"session\":\"AM\",\"entries\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(milk() + "/sheet").param("date", today.toString()).param("session", "DAY").with(as(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void singleCowDayIsGradedAgainstExpectedYield() throws Exception {
        String url = milk() + "/cows/" + ponni + "/days/" + today;
        mvc.perform(put(url).with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"am\":8,\"pm\":7,\"fatPct\":3.4,\"snfPct\":8.6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.animal.tag").value("VD-002"))
                .andExpect(jsonPath("$.expectation.daysInMilk").value(84))
                .andExpect(jsonPath("$.expectation.source").value("BREED"))
                .andExpect(jsonPath("$.expectation.expectedLitres").value(16.4))
                .andExpect(jsonPath("$.day.total").value(15.0))
                .andExpect(jsonPath("$.day.pctOfExpected").value(91))
                .andExpect(jsonPath("$.day.grade").value("B"))
                .andExpect(jsonPath("$.day.fatPct").value(3.4));

        mvc.perform(put(url).with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"am\":8,\"pm\":7,\"fatPct\":3.0}"))
                .andExpect(jsonPath("$.day.grade").value("C"))
                .andExpect(jsonPath("$.day.qualityIssues[0]").value("Fat 3 % is below 3.2 %"));

        mvc.perform(get(milk() + "/grades").param("from", today.minusDays(29).toString()).param("to", today.toString()).with(as(owner)))
                .andExpect(jsonPath("$.C").value(1))
                .andExpect(jsonPath("$.A").value(0));

        // Next day the form shows her last values
        jdbc.update("update animal set last_calving_date = last_calving_date where id = ?", ponni);
        mvc.perform(put(url).with(as(worker)).contentType(MediaType.APPLICATION_JSON).content("{\"am\":8}"))
                .andExpect(jsonPath("$.day.pm").doesNotExist())
                .andExpect(jsonPath("$.day.grade").doesNotExist());
        mvc.perform(get(milk() + "/cows/" + ponni).param("from", today.minusDays(7).toString()).param("to", today.toString()).with(as(owner)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].am").value(8.0));
    }

    @Test
    void ownerSetsGradeRulesWorkersCannot() throws Exception {
        mvc.perform(get(milk() + "/grading").with(as(worker)))
                .andExpect(jsonPath("$.bandA").value(100))
                .andExpect(jsonPath("$.breeds[0].breed").value("HF"))
                .andExpect(jsonPath("$.minFatPct.limit").value(3.2));

        String rules = "{\"basis\":\"BREED\",\"bandA\":95,\"bandB\":85,\"bandC\":70,"
                + "\"breeds\":[{\"breed\":\"HF\",\"peakLitres\":20,\"peakDay\":50}]}";
        mvc.perform(put(milk() + "/grading").with(as(worker)).contentType(MediaType.APPLICATION_JSON).content(rules))
                .andExpect(status().isForbidden());
        mvc.perform(put(milk() + "/grading").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(rules))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bandA").value(95))
                .andExpect(jsonPath("$.startPct").value(70));
        mvc.perform(get(milk() + "/cows/" + ponni + "/days/" + today).with(as(owner)))
                .andExpect(jsonPath("$.expectation.peakLitres").value(20.0));

        mvc.perform(put(milk() + "/grading").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bandA\":80,\"bandB\":90,\"bandC\":70}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(milk() + "/grading").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startPct\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startPct").exists());
    }

    @Test
    void sheetShowsStageAndSoldSessionLocksTheMilkLog() throws Exception {
        mvc.perform(get(milk() + "/sheet").param("date", today.toString()).param("session", "AM").with(as(worker)))
                .andExpect(jsonPath("$.sold").value(false))
                .andExpect(jsonPath("$.cows[0].stage").value("MID"))    // no calving date
                .andExpect(jsonPath("$.cows[1].stage").value("EARLY")); // 84 days in milk

        sheet(worker, "AM", "{\"animalId\":" + ponni + ",\"litres\":8.2}");
        mvc.perform(get(milk() + "/grades").param("from", today.toString()).param("to", today.toString())
                        .param("session", "AM").with(as(owner)))
                .andExpect(jsonPath("$.A").value(1)); // 8.2 L vs half of 16.4 L expected

        mvc.perform(put(milk() + "/sales").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\",\"session\":\"AM\",\"soldQty\":8}"))
                .andExpect(status().isOk());
        mvc.perform(get(milk() + "/sheet").param("date", today.toString()).param("session", "AM").with(as(worker)))
                .andExpect(jsonPath("$.sold").value(true));
        mvc.perform(put(milk() + "/sheet").with(as(worker)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\",\"session\":\"AM\",\"entries\":[{\"animalId\":" + ponni + ",\"litres\":9}]}"))
                .andExpect(status().isConflict());

        // One cow: the sold morning is locked; evening alone can still be saved
        String url = milk() + "/cows/" + ponni + "/days/" + today;
        mvc.perform(get(url).with(as(worker)))
                .andExpect(jsonPath("$.amSold").value(true))
                .andExpect(jsonPath("$.pmSold").value(false));
        mvc.perform(put(url).with(as(worker)).contentType(MediaType.APPLICATION_JSON).content("{\"am\":9,\"pm\":7}"))
                .andExpect(status().isConflict());
        mvc.perform(put(url).with(as(worker)).contentType(MediaType.APPLICATION_JSON).content("{\"pm\":7,\"sessions\":[\"PM\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day.am").value(8.2))
                .andExpect(jsonPath("$.day.pm").value(7.0));

        // A whole-day milk sale can't sit beside a morning sale
        mvc.perform(put(milk() + "/sales").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\",\"session\":\"DAY\",\"soldQty\":10}"))
                .andExpect(status().isConflict());
    }

    @Test
    void milkInWithdrawalIsWithheldAndCantBeSold() throws Exception {
        long buyer = Daily.contact(mvc, json, owner, farmId, "Aavin society", "\"BUYER\"", "\"milkRate\":38");
        sheet(owner, "AM", "{\"animalId\":" + lakshmi + ",\"litres\":9},{\"animalId\":" + ponni + ",\"litres\":8}");

        // Treated yesterday with 3 days' milk withdrawal: today's milk is withheld
        mvc.perform(post("/api/v1/farms/" + farmId + "/health/records").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"animalId\":" + lakshmi + ",\"date\":\"" + today.minusDays(1) + "\",\"type\":\"TREATMENT\","
                                + "\"medicine\":\"Ceftriaxone\",\"milkWithdrawalDays\":3}"))
                .andExpect(status().isCreated());

        mvc.perform(get(milk() + "/sheet").param("date", today.toString()).param("session", "AM").with(as(owner)))
                .andExpect(jsonPath("$.cows[0].inWithdrawal").value(true))
                .andExpect(jsonPath("$.cows[0].withdrawalUntil").exists())
                .andExpect(jsonPath("$.cows[1].inWithdrawal").value(false))
                .andExpect(jsonPath("$.withheldLitres").value(9.0));
        mvc.perform(get(milk() + "/day").param("date", today.toString()).with(as(owner)))
                .andExpect(jsonPath("$.am.withheldLitres").value(9.0))
                .andExpect(jsonPath("$.cows[0].withheld").value(true));

        String sale = "{\"date\":\"" + today + "\",\"session\":\"AM\",\"soldQty\":%s,\"buyerContactId\":" + buyer + "}";
        mvc.perform(put(milk() + "/sales").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(sale.formatted("12")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only 8.0 L can be sold: 9.0 L is from cows in milk withdrawal."));
        mvc.perform(put(milk() + "/sales").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(sale.formatted("7.5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(17.0))
                .andExpect(jsonPath("$.cowLitres").value(17.0))
                .andExpect(jsonPath("$.withheldLitres").value(9.0))
                .andExpect(jsonPath("$.otherUseQty").value(9.5))
                .andExpect(jsonPath("$.rate").value(38.0))
                .andExpect(jsonPath("$.incomeAmount").value(285.0))
                .andExpect(jsonPath("$.buyer.name").value("Aavin society"));

        // Workers see litres but not money; their save keeps the owner's rate
        mvc.perform(put(milk() + "/sales").with(as(worker)).contentType(MediaType.APPLICATION_JSON).content(sale.formatted("8")))
                .andExpect(jsonPath("$.rate").doesNotExist())
                .andExpect(jsonPath("$.incomeAmount").doesNotExist());
        String list = mvc.perform(get(milk() + "/sales").param("from", today.toString()).param("to", today.toString()).with(as(owner)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].incomeAmount").value(304.0))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(list).get(0).get("id").asLong();

        mvc.perform(delete(milk() + "/sales/" + id).with(as(worker))).andExpect(status().isForbidden());
        mvc.perform(delete(milk() + "/sales/" + id).with(as(owner))).andExpect(status().isNoContent());
    }

    @Test
    void buyerMustBeSavedAsBuyer() throws Exception {
        long vet = Daily.contact(mvc, json, owner, farmId, "Dr. Kumar", "\"VET\"", "");
        mvc.perform(put(milk() + "/sales").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + today + "\",\"session\":\"DAY\",\"quantity\":20,\"soldQty\":20,\"buyerContactId\":" + vet + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dr. Kumar isn't saved as a milk buyer. Edit the contact to add it."));
    }
}
