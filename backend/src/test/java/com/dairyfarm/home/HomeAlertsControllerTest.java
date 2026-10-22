package com.dairyfarm.home;

import static com.dairyfarm.support.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dairyfarm.support.Api;
import com.dairyfarm.support.Daily;
import com.dairyfarm.support.IntegrationTest;
import com.dairyfarm.support.TestTokens.TestUser;

import tools.jackson.databind.JsonNode;

class HomeAlertsControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private long farmId;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
        today = Daily.today();
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

    /** All alerts in every group of GET /alerts. */
    private List<JsonNode> alerts(TestUser who, String query) throws Exception {
        JsonNode r = call(get(base() + "/alerts" + query), who, null, 200);
        List<JsonNode> out = new ArrayList<>();
        for (String g : List.of("overdue", "today", "thisWeek", "later")) {
            r.get(g).forEach(out::add);
        }
        return out;
    }

    private List<JsonNode> withCode(List<JsonNode> list, String code) {
        return list.stream().filter(a -> code.equals(a.path("ruleCode").asString(""))).toList();
    }

    private long cow(String tag, String extra) throws Exception {
        return Daily.animal(mvc, json, owner, farmId, tag, "MILKING", extra);
    }

    private void milk(long cow, LocalDate date, String body) throws Exception {
        call(put(base() + "/milk/cows/" + cow + "/days/" + date), owner, body, 200);
    }

    @Test
    void pregnancyCheckAlertIsRaisedThenClosesWhenTheCheckIsRecorded() throws Exception {
        long ponni = cow("VD-002", "\"name\":\"Ponni\",\"lastCalvingDate\":\"" + today.minusDays(120) + "\"");
        long semen = call(post(base() + "/semen"), owner, "{\"bullName\":\"Bull A\",\"bullCode\":\"HF-1\",\"breed\":\"HF\","
                + "\"expiryDate\":\"" + today.plusYears(1) + "\",\"costPerStraw\":450,\"straws\":5}", 201).get("id").asLong();
        long svc = call(post(base() + "/breeding/services"), owner, "{\"animalId\":" + ponni + ",\"date\":\"" + today.minusDays(50)
                + "\",\"method\":\"AI\",\"semenItemId\":" + semen + "}", 201).get("id").asLong();

        List<JsonNode> pd = withCode(alerts(owner, ""), "PD_DUE");
        assertThat(pd).hasSize(1);
        assertThat(pd.get(0).get("severity").asString()).isEqualTo("HIGH");
        assertThat(pd.get(0).get("overdueDays").asInt()).isEqualTo(5);
        assertThat(pd.get(0).get("category").asString()).isEqualTo("REPRODUCTION");
        assertThat(pd.get(0).get("action").get("kind").asString()).isEqualTo("LOG_PD");
        assertThat(pd.get(0).get("action").get("params").get("serviceId").asLong()).isEqualTo(svc);
        assertThat(pd.get(0).get("why").asString()).contains("45 days after service");
        assertThat(withCode(alerts(worker, ""), "PD_DUE")).hasSize(1);

        // a worker can't dismiss a rule alert, only mark it done
        call(post(base() + "/alerts/" + pd.get(0).get("id").asLong() + "/dismiss"), worker, null, 403);

        // 4 straws left: the owner sees "semen stock low", workers don't (finance & ops rules are owner-only by default)
        assertThat(withCode(alerts(owner, ""), "SEMEN_LOW")).hasSize(1);
        assertThat(withCode(alerts(worker, ""), "SEMEN_LOW")).isEmpty();

        // No manual refresh: saving the check marks the alerts out of date, and the next read closes it.
        call(post(base() + "/breeding/services/" + svc + "/pregnancy-check"), owner,
                "{\"date\":\"" + today + "\",\"method\":\"BLOOD\",\"result\":\"PREGNANT\"}", 200);
        assertThat(withCode(alerts(owner, ""), "PD_DUE")).isEmpty();
        List<JsonNode> done = withCode(alerts(owner, "?status=DONE"), "PD_DUE");
        assertThat(done).hasSize(1);
        assertThat(done.get(0).get("autoClosed").asBoolean()).isTrue();
    }

    @Test
    void withdrawalAndYieldDropFollowRulesAndWorkerVisibility() throws Exception {
        long cow = cow("VD-001", "\"name\":\"Lakshmi\",\"lastCalvingDate\":\"" + today.minusDays(60) + "\"");
        for (int d = 8; d >= 1; d--) {
            milk(cow, today.minusDays(d), "{\"am\":10}");
        }
        milk(cow, today, "{\"am\":6}");
        call(post(base() + "/health/records"), owner, "{\"animalId\":" + cow + ",\"date\":\"" + today
                + "\",\"type\":\"TREATMENT\",\"medicine\":\"Ceftriaxone\",\"milkWithdrawalDays\":3}", 201);

        List<JsonNode> all = alerts(owner, "");
        JsonNode drop = withCode(all, "YIELD_DROP").get(0);
        assertThat(drop.get("title").asString()).isEqualTo("Sudden yield drop −40 %");
        assertThat(drop.get("category").asString()).isEqualTo("MILK");
        JsonNode w = withCode(all, "WITHDRAWAL").get(0);
        assertThat(w.get("severity").asString()).isEqualTo("HIGH");
        assertThat(w.get("detail").asString()).contains("Ceftriaxone");

        // hide yield drops from workers; the threshold is the milk grading drop % (stored once)
        call(put(base() + "/alerts/rules"), owner, "[{\"code\":\"YIELD_DROP\",\"workerVisible\":false}]", 200);
        assertThat(withCode(alerts(worker, ""), "YIELD_DROP")).isEmpty();
        assertThat(withCode(alerts(worker, ""), "WITHDRAWAL")).hasSize(1);
        assertThat(withCode(alerts(owner, ""), "YIELD_DROP")).hasSize(1);

        JsonNode rules = call(put(base() + "/alerts/rules"), owner, "[{\"code\":\"YIELD_DROP\",\"params\":{\"dropAlertPct\":50}}]", 200);
        assertThat(rules.findValues("code").stream().map(JsonNode::asString)).contains("WITHDRAWAL", "LOW_MARGIN");
        assertThat(call(get(base() + "/milk/grading"), owner, null, 200).get("dropAlertPct").asInt()).isEqualTo(50);
        assertThat(withCode(alerts(owner, ""), "YIELD_DROP")).isEmpty();

        // shared numbers: PD days go to the breeding settings
        call(put(base() + "/alerts/rules"), owner, "[{\"code\":\"PD_DUE\",\"params\":{\"pdAfterDays\":40}}]", 200);
        assertThat(call(get(base() + "/breeding/settings"), owner, null, 200).get("pdAfterDays").asInt()).isEqualTo(40);
        call(put(base() + "/alerts/rules"), owner, "[{\"code\":\"BCS_RANGE\",\"params\":{\"min\":9}}]", 400);
        call(put(base() + "/alerts/rules"), owner, "[{\"code\":\"WITHDRAWAL\",\"enabled\":false}]", 200);
        assertThat(withCode(alerts(owner, ""), "WITHDRAWAL")).hasSize(1);
        call(get(base() + "/alerts/rules"), worker, null, 403);
    }

    @Test
    void calfScheduleAndWeightAlertsCloseWhenRecorded() throws Exception {
        long calf = Daily.animal(mvc, json, owner, farmId, "VD-019", "CALF", "\"dateOfBirth\":\"" + today.minusDays(15) + "\"");
        JsonNode schedules = call(get(base() + "/health-schedules"), worker, null, 200);
        assertThat(schedules.size()).isEqualTo(2);
        long deworm = schedules.get(0).get("type").asString().equals("DEWORMING") ? schedules.get(0).get("id").asLong()
                : schedules.get(1).get("id").asLong();

        List<JsonNode> all = alerts(owner, "");
        JsonNode due = withCode(all, "SCHEDULE_DUE").get(0);
        assertThat(due.get("title").asString()).isEqualTo("Deworming due — Day 15");
        assertThat(due.get("action").get("params").get("step").asString()).isEqualTo("0-0");
        assertThat(due.get("action").get("params").get("scheduleId").asLong()).isEqualTo(deworm);
        assertThat(withCode(all, "WEIGHT_OVERDUE")).hasSize(1);

        call(post(base() + "/health/records"), worker, "{\"animalId\":" + calf + ",\"date\":\"" + today
                + "\",\"type\":\"DEWORMING\",\"medicine\":\"Piperazine\",\"scheduleId\":" + deworm + ",\"scheduleStep\":\"0-0\"}", 201);
        call(post(base() + "/animals/" + calf + "/measurements"), worker,
                "{\"date\":\"" + today + "\",\"method\":\"TAPE\",\"weightKg\":38}", 201);
        call(post(base() + "/alerts/refresh"), owner, null, 200);
        all = alerts(owner, "");
        assertThat(withCode(all, "SCHEDULE_DUE")).isEmpty();
        assertThat(withCode(all, "WEIGHT_OVERDUE")).isEmpty();

        call(post(base() + "/health-schedules"), worker, "{\"type\":\"VACCINATION\",\"name\":\"HS\",\"appliesTo\":\"ALL\","
                + "\"steps\":[{\"label\":\"At 6 months\",\"atAgeDays\":180}]}", 403);
    }

    @Test
    void repeatingReminderComesBackAfterDoneAndSnoozeHidesIt() throws Exception {
        JsonNode r = call(post(base() + "/reminders"), worker, "{\"title\":\"Clean milk cans\",\"notes\":\"use hot water\",\"date\":\""
                + today + "\",\"type\":\"OTHER\",\"repeat\":\"WEEKLY\"}", 201);
        assertThat(r.get("source").asString()).isEqualTo("REMINDER");
        assertThat(r.get("notes").asString()).isEqualTo("use hot water");
        long id = r.get("id").asLong();
        call(post(base() + "/alerts/" + id + "/done"), worker, null, 200);
        List<JsonNode> open = alerts(owner, "").stream().filter(a -> a.get("title").asString().equals("Clean milk cans")).toList();
        assertThat(open).hasSize(1);
        assertThat(open.get(0).get("dueDate").asString()).isEqualTo(today.plusWeeks(1).toString());

        long next = open.get(0).get("id").asLong();
        call(post(base() + "/alerts/" + next + "/snooze"), owner, "{\"until\":\"" + java.time.Instant.now().plusSeconds(3600) + "\"}", 200);
        assertThat(alerts(owner, "").stream().filter(a -> a.get("id").asLong() == next)).isEmpty();

        // owner-only reminder is hidden from workers
        long secret = call(post(base() + "/reminders"), owner, "{\"title\":\"Pay bank loan\",\"date\":\"" + today
                + "\",\"workerVisible\":false}", 201).get("id").asLong();
        call(get(base() + "/alerts/" + secret), worker, null, 404);
        call(put(base() + "/reminders/" + secret), worker, "{\"title\":\"x\",\"date\":\"" + today + "\"}", 404);
    }

    @Test
    void checklistTicksItselfFromRecordsAndKeepsCustomTasks() throws Exception {
        long cow = cow("VD-001", "");
        JsonNode cl = call(get(base() + "/checklist"), worker, null, 200);
        assertThat(cl.get("total").asInt()).isEqualTo(6);
        assertThat(cl.get("done").asInt()).isZero();

        milk(cow, today, "{\"am\":9}");
        call(post(base() + "/barn-readings"), worker, "{\"date\":\"" + today + "\",\"time\":\"14:00\",\"temperatureC\":34,"
                + "\"humidityPct\":62}", 201);
        cl = call(get(base() + "/checklist"), worker, null, 200);
        assertThat(task(cl, "MILK_AM").get("done").asBoolean()).isTrue();
        assertThat(task(cl, "MILK_AM").get("auto").asBoolean()).isTrue();
        assertThat(task(cl, "HEAT_STRESS").get("detail").asString()).startsWith("THI 86");
        call(put(base() + "/checklist/MILK_AM"), worker, "{\"done\":false}", 400);
        cl = call(put(base() + "/checklist/FEED_STOCK"), worker, "{\"done\":true}", 200);
        assertThat(task(cl, "FEED_STOCK").get("doneBy").get("name").asString()).isEqualTo("Murugan");
        assertThat(cl.get("done").asInt()).isEqualTo(3);

        call(post(base() + "/checklist/tasks"), worker, "{\"name\":\"Clean troughs\"}", 403);
        cl = call(post(base() + "/checklist/tasks"), owner, "{\"name\":\"Check loan papers\",\"workerVisible\":false}", 201);
        assertThat(cl.get("custom").get(0).get("repeatDaily").asBoolean()).isTrue();
        assertThat(cl.get("custom").get(0).get("link").get("kind").asString()).isEqualTo("CHECKLIST");
        assertThat(call(get(base() + "/checklist"), worker, null, 200).get("custom").size()).isZero();
        assertThat(call(get(base() + "/checklist"), owner, null, 200).get("total").asInt()).isEqualTo(7);
    }

    private static JsonNode task(JsonNode cl, String key) {
        for (JsonNode t : cl.get("builtIn")) {
            if (t.get("key").asString().equals(key)) {
                return t;
            }
        }
        throw new AssertionError("no task " + key);
    }

    @Test
    void homeShowsMoneyToOwnersOnly() throws Exception {
        long cow = cow("VD-001", "\"name\":\"Lakshmi\",\"lastCalvingDate\":\"" + today.minusDays(60) + "\"");
        milk(cow, today.minusDays(1), "{\"am\":10,\"pm\":8}");
        milk(cow, today, "{\"am\":9.5}");
        call(post(base() + "/health/records"), owner, "{\"animalId\":" + cow + ",\"date\":\"" + today
                + "\",\"type\":\"TREATMENT\",\"milkWithdrawalDays\":2}", 201);

        JsonNode o = call(get(base() + "/home"), owner, null, 200);
        assertThat(o.get("role").asString()).isEqualTo("OWNER");
        assertThat(o.get("greeting").asString()).isEqualTo("Bharath");
        assertThat(o.get("tiles").get("milkToday").asDouble()).isEqualTo(9.5);
        assertThat(o.get("tiles").get("milkCompareNote").asString()).isEqualTo("vs yesterday (AM only)");
        assertThat(o.get("tiles").get("milkChangePct").asDouble()).isEqualTo(-5.0);
        assertThat(o.get("tiles").get("milkingCows").asInt()).isEqualTo(1);
        assertThat(o.get("tiles").get("marginAlertPct").asInt()).isEqualTo(30);
        assertThat(o.get("topProducers").get(0).get("animal").get("tag").asString()).isEqualTo("VD-001");
        assertThat(o.get("tasks").get(0).get("status").asString()).isEqualTo("DONE");
        assertThat(o.get("tasks").get(0).get("link").get("kind").asString()).isEqualTo("MILK_AM");

        JsonNode w = call(get(base() + "/home"), worker, null, 200);
        assertThat(w.get("role").asString()).isEqualTo("WORKER");
        assertThat(w.get("tiles").hasNonNull("marginMonthPct")).isFalse();
        assertThat(w.get("tiles").hasNonNull("marginAlertPct")).isFalse();
        assertThat(w.get("topProducers").size()).isZero();
        assertThat(w.get("healthReminders").get(0).get("ruleCode").asString()).isEqualTo("WITHDRAWAL");
        assertThat(w.get("feedingReminders").get(0).get("title").asString()).isEqualTo("Feed AM");
    }

    @Test
    void reportsGiveCsvAndJsonToOwners() throws Exception {
        long cow = cow("VD-001", "\"name\":\"Lakshmi\"");
        LocalDate d = today.minusDays(1);
        milk(cow, d, "{\"am\":10,\"pm\":8,\"fatPct\":4.1}");
        String period = "?from=" + d + "&to=" + today;

        String csv = mvc.perform(get(base() + "/reports/milk.csv" + period).with(as(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("Tag,Name,Days,AM L,PM L,Total L,L/day,Withheld L,Fat %,SNF %");
        assertThat(csv).contains("VD-001,Lakshmi,1,10,8,18,18,0,4.1,");
        assertThat(csv).contains("Total litres,18");

        JsonNode milkJson = call(get(base() + "/reports/milk" + period), owner, null, 200);
        assertThat(milkJson.get("totals").get("farmAvgPerDay").asDouble()).isEqualTo(18.0);
        assertThat(milkJson.get("columns").size()).isEqualTo(10);

        JsonNode s = call(get(base() + "/reports/summary" + period), owner, null, 200);
        assertThat(s.get("milkLitres").asDouble()).isEqualTo(18.0);
        call(get(base() + "/reports/pnl" + period), owner, null, 200);
        call(get(base() + "/reports/nope"), owner, null, 404);
        call(get(base() + "/reports/milk.csv"), worker, null, 403);
        call(get(base() + "/reports/summary"), worker, null, 403);
    }

    @Test
    void cullAdvisorScoresAndCompareCows() throws Exception {
        long old = cow("VD-003", "\"name\":\"Kamala\",\"dateOfBirth\":\"" + today.minusYears(10) + "\",\"lastCalvingDate\":\""
                + today.minusDays(250) + "\"");
        long young = cow("VD-011", "\"name\":\"Malar\",\"dateOfBirth\":\"" + today.minusYears(4) + "\",\"lastCalvingDate\":\""
                + today.minusDays(30) + "\"");
        for (int i = 1; i <= 3; i++) {
            call(post(base() + "/health/records"), owner, "{\"animalId\":" + old + ",\"date\":\"" + today.minusDays(i * 10)
                    + "\",\"type\":\"TREATMENT\",\"cost\":500}", 201);
        }
        milk(young, today.minusDays(1), "{\"am\":8,\"pm\":7}");

        JsonNode ca = call(get(base() + "/reports/cull-advisor"), owner, null, 200);
        JsonNode kamala = row(ca, "VD-003"), malar = row(ca, "VD-011");
        assertThat(kamala.get("verdict").asString()).isEqualTo("CULL");
        assertThat(kamala.get("reasons").toString()).contains("3 treatments in 90 days", "Past replacement age (10 y)");
        assertThat(malar.get("verdict").asString()).isEqualTo("KEEP");
        assertThat(malar.get("production").asInt()).isGreaterThanOrEqualTo(90);
        assertThat(ca.get("counts").get("cull").asInt()).isEqualTo(1);

        JsonNode kept = call(post(base() + "/reports/cull-advisor/" + old + "/keep"), owner, null, 200);
        assertThat(kept.get("verdict").asString()).isEqualTo("MONITOR");
        assertThat(kept.get("keepUntil").asString()).isEqualTo(today.plusDays(90).toString());

        JsonNode cmp = call(get(base() + "/reports/compare?a=" + old + "&b=" + young), owner, null, 200);
        assertThat(cmp.get("days").size()).isEqualTo(7);
        assertThat(cmp.get("b").get("litres7d").size()).isEqualTo(7);
        assertThat(cmp.get("b").get("avgLitres").asDouble()).isEqualTo(15.0);
        assertThat(cmp.get("a").get("health").get("count").asInt()).isEqualTo(3);
        assertThat(cmp.get("b").get("stage").asString()).isEqualTo("Early (0–100)");

        JsonNode dried = call(post(base() + "/reports/cull-advisor/" + young + "/dry"), owner, null, 200);
        assertThat(dried.get("animal").get("category").asString()).isEqualTo("DRY");
        call(get(base() + "/reports/cull-advisor"), worker, null, 403);
    }

    private static JsonNode row(JsonNode ca, String tag) {
        for (JsonNode r : ca.get("cows")) {
            if (r.get("animal").get("tag").asString().equals(tag)) {
                return r;
            }
        }
        throw new AssertionError("no row " + tag);
    }
}
