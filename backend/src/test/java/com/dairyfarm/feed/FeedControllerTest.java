package com.dairyfarm.feed;

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

class FeedControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private long farmId;
    private long supplier;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = Daily.today();
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
        supplier = Daily.contact(mvc, json, owner, farmId, "Feed Store", "\"SUPPLIER\"", "");
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

    private long item(String name, String category, String extra) throws Exception {
        return call(post(base() + "/feed/items"), owner, "{\"name\":\"" + name + "\",\"category\":\"" + category + "\""
                + (extra.isEmpty() ? "" : "," + extra) + "}", 201).get("id").asLong();
    }

    private void buy(long itemId, String qty, String price) throws Exception {
        call(post(base() + "/feed/purchases"), owner, "{\"itemId\":" + itemId + ",\"date\":\"" + today + "\",\"quantity\":" + qty
                + ",\"costPerUnit\":" + price + "}", 201);
    }

    @Test
    void itemsPurchasesStockAndMoneyHiddenFromWorkers() throws Exception {
        long gnc = item("Groundnut cake", "CONCENTRATE_INGREDIENT", "\"cpPct\":45,\"reorderLevel\":40,\"leadTimeDays\":3,"
                + "\"supplierContactId\":" + supplier);
        long napier = item("CO5 Napier", "GREEN_FODDER", "");
        long unused = item("Old feed", "DRY_FODDER", "");
        call(post(base() + "/feed/items"), owner, "{\"name\":\"groundnut cake\",\"category\":\"MIX\"}", 409);
        call(post(base() + "/feed/items"), worker, "{\"name\":\"Wheat bran\",\"category\":\"CONCENTRATE_INGREDIENT\"}", 403);
        // Supplier must be saved as a supplier
        long vet = Daily.contact(mvc, json, owner, farmId, "Dr. Kumar", "\"VET\"", "");
        call(post(base() + "/feed/items"), owner, "{\"name\":\"Bran\",\"category\":\"CONCENTRATE_INGREDIENT\",\"supplierContactId\":"
                + vet + "}", 400);

        // A worker records a purchase: stock up, item cost updated, money hidden from them
        JsonNode bought = call(post(base() + "/feed/purchases"), worker, "{\"itemId\":" + gnc + ",\"date\":\"" + today
                + "\",\"quantity\":100,\"totalAmount\":4200,\"paymentMethod\":\"UPI\",\"paid\":false}", 201);
        assertThat(bought.get("item").get("stock").asDouble()).isEqualTo(100.0);
        assertThat(bought.get("move").get("cost")).isNull();
        assertThat(bought.get("move").get("supplier").get("name").asString()).isEqualTo("Feed Store");
        JsonNode asOwner = call(get(base() + "/feed/items/" + gnc), owner, null, 200);
        assertThat(asOwner.get("costPerUnit").asDouble()).isEqualTo(42.0);
        assertThat(asOwner.get("stock").get("stock").asDouble()).isEqualTo(100.0);
        assertThat(asOwner.get("stock").get("daysLeft")).isNull();
        assertThat(asOwner.get("stock").get("barPct").asInt()).isEqualTo(100);
        assertThat(call(get(base() + "/feed/items/" + gnc), worker, null, 200).get("costPerUnit")).isNull();
        JsonNode purchases = call(get(base() + "/feed/purchases"), owner, null, 200);
        assertThat(purchases.get(0).get("cost").asDouble()).isEqualTo(4200.0);
        assertThat(purchases.get(0).get("costPerUnit").asDouble()).isEqualTo(42.0);
        assertThat(purchases.get(0).get("paid").asBoolean()).isFalse();

        // Own fodder harvest and a correction
        call(post(base() + "/feed/items/" + napier + "/stock"), worker, "{\"type\":\"HARVEST\",\"date\":\"" + today
                + "\",\"quantity\":500}", 200);
        JsonNode adj = call(post(base() + "/feed/items/" + napier + "/stock"), worker, "{\"type\":\"ADJUSTMENT\",\"date\":\"" + today
                + "\",\"quantity\":-20,\"notes\":\"spoiled\"}", 200);
        assertThat(adj.get("stock").asDouble()).isEqualTo(480.0);
        JsonNode moves = call(get(base() + "/feed/items/" + napier + "/moves"), owner, null, 200);
        assertThat(moves.toString()).contains("\"HARVEST\"", "\"ADJUSTMENT\"");

        // Delete: used item refused, unused item gone
        call(delete(base() + "/feed/items/" + gnc), owner, null, 409);
        call(delete(base() + "/feed/items/" + unused), worker, null, 403);
        call(delete(base() + "/feed/items/" + unused), owner, null, 204);
        assertThat(call(get(base() + "/feed/items"), owner, null, 200).size()).isEqualTo(2);
    }

    @Test
    void formulaCostNutrientsAndPreparingABatch() throws Exception {
        long maize = item("Maize", "CONCENTRATE_INGREDIENT", "\"dmPct\":88,\"cpPct\":9");
        long gingelly = item("Gingelly cake", "CONCENTRATE_INGREDIENT", "\"dmPct\":90,\"cpPct\":35,\"maxInclusionPct\":5");
        long cotton = item("Cottonseed cake", "CONCENTRATE_INGREDIENT", "\"excluded\":true,\"excludedReason\":\"gossypol\"");
        long mix = item("Concentrate mix", "MIX", "");
        buy(maize, "200", "20");
        buy(gingelly, "5", "50");

        JsonNode bad = call(post(base() + "/feed/formulas"), owner, "{\"name\":\"Villupuram 100 kg\",\"mixItemId\":" + mix
                + ",\"batchKg\":100,\"lines\":[{\"itemId\":" + maize + ",\"kg\":90},{\"itemId\":" + cotton + ",\"kg\":10}]}", 400);
        assertThat(bad.get("detail").asString()).contains("Cottonseed cake can't be added", "gossypol");
        call(post(base() + "/feed/formulas"), worker, "{\"name\":\"X\",\"mixItemId\":" + mix + ",\"batchKg\":100,\"lines\":[{\"itemId\":"
                + maize + ",\"kg\":100}]}", 403);

        JsonNode f = call(post(base() + "/feed/formulas"), owner, "{\"name\":\"Villupuram 100 kg\",\"mixItemId\":" + mix
                + ",\"batchKg\":100,\"targets\":{\"cpPct\":18,\"costMin\":21,\"costMax\":27},\"lines\":[{\"itemId\":" + gingelly
                + ",\"kg\":7,\"sortOrder\":2},{\"itemId\":" + maize + ",\"kg\":93,\"sortOrder\":1}]}", 201);
        long fid = f.get("id").asLong();
        assertThat(f.get("lines").get(0).get("item").get("name").asString()).isEqualTo("Maize");
        assertThat(f.get("costPerKg").asDouble()).isEqualTo(22.10);   // (93×20 + 7×50) / 100
        assertThat(f.get("lines").get(1).get("overMax").asBoolean()).isTrue();
        assertThat(f.get("warnings").toString()).contains("Gingelly cake 7 % is above its max inclusion 5 %");
        JsonNode cp = f.get("nutrients").get(1);
        assertThat(cp.get("name").asString()).isEqualTo("CP");
        assertThat(cp.get("mixPct").asDouble()).isEqualTo(10.82);       // (93×9 + 7×35) / 100
        assertThat(cp.get("status").asString()).isEqualTo("LOW");
        assertThat(call(get(base() + "/feed/formulas/" + fid), worker, null, 200).get("costPerKg")).isNull();

        // 200 kg needs 14 kg gingelly cake, only 5 in stock
        JsonNode pv = call(get(base() + "/feed/formulas/" + fid + "/batch-preview?batchKg=200"), worker, null, 200);
        assertThat(pv.get("shortCount").asInt()).isEqualTo(1);
        assertThat(pv.get("lines").get(1).get("shortKg").asDouble()).isEqualTo(9.0);
        JsonNode refused = call(post(base() + "/feed/formulas/" + fid + "/batches"), worker, "{\"date\":\"" + today
                + "\",\"batchKg\":200}", 400);
        assertThat(refused.get("detail").asString()).contains("Gingelly cake is short by 9 kg");

        buy(gingelly, "10", "50");
        JsonNode done = call(post(base() + "/feed/formulas/" + fid + "/batches"), worker, "{\"date\":\"" + today
                + "\",\"batchKg\":100}", 201);
        assertThat(done.get("mix").get("stock").asDouble()).isEqualTo(100.0);
        assertThat(done.get("preview").get("batchCost")).isNull();          // worker
        JsonNode mixItem = call(get(base() + "/feed/items/" + mix), owner, null, 200);
        assertThat(mixItem.get("costPerUnit").asDouble()).isEqualTo(22.10);
        assertThat(call(get(base() + "/feed/items/" + maize), owner, null, 200).get("stock").get("stock").asDouble()).isEqualTo(107.0);
        assertThat(call(get(base() + "/feed/items/" + gingelly), owner, null, 200).get("stock").get("stock").asDouble()).isEqualTo(8.0);
        JsonNode mixMoves = call(get(base() + "/feed/items/" + mix + "/moves"), owner, null, 200);
        assertThat(mixMoves.get(0).get("type").asString()).isEqualTo("PRODUCE");
        assertThat(mixMoves.get(0).get("notes").asString()).contains("prepared by Murugan");
    }

    @Test
    void rationRulesFeedingSheetNeedAndOverview() throws Exception {
        long velimasal = item("Velimasal", "GREEN_FODDER", "\"leadTimeDays\":2");
        long napier = item("CO5 Napier", "GREEN_FODDER", "");
        long straw = item("Paddy straw", "DRY_FODDER", "");
        long mix = item("Concentrate mix", "MIX", "");
        long cow = Daily.animal(mvc, json, owner, farmId, "VD-001", "MILKING", "\"name\":\"Lakshmi\",\"currentWeightKg\":450,"
                + "\"lastCalvingDate\":\"" + today.minusDays(83) + "\"");
        Daily.animal(mvc, json, owner, farmId, "VD-008", "DRY", "\"name\":\"Kaveri\"");
        call(put(base() + "/milk/cows/" + cow + "/days/" + today.minusDays(1)), owner, "{\"am\":9.5,\"pm\":9}", 200);

        JsonNode r = call(get(base() + "/feed/ration?animalId=" + cow), worker, null, 200);
        assertThat(r.get("bodyWeightKg").asDouble()).isEqualTo(450.0);
        assertThat(r.get("weightSource").asString()).isEqualTo("ANIMAL");
        assertThat(r.get("milkYesterday").asDouble()).isEqualTo(18.5);
        // TMR: DMI 450 × 3.75 % = 16.875 kg DM; concentrate 1.5 + 18.5 × 0.4 = 8.9 kg (× 80 % = 7.12 kg DM);
        // forage DM 9.755 split 45 : 15 → green 7.316 ÷ 30 % = 24.39 kg, dry 2.439 ÷ 90 % = 2.71 kg (no DM % on items)
        assertThat(r.get("totals").get("targetKg").asDouble()).isEqualTo(16.88);
        assertThat(r.get("totals").get("dmiPct").asDouble()).isEqualTo(3.75);
        assertThat(r.get("totals").get("waterL").asDouble()).isEqualTo(104.0);
        assertThat(r.get("totals").get("concentrateKg").asDouble()).isEqualTo(8.9);
        assertThat(r.get("totals").get("greenKg").asDouble()).isEqualTo(24.39);
        assertThat(r.get("totals").get("dryKg").asDouble()).isEqualTo(2.71);
        assertThat(r.get("totals").get("totalKg").asDouble()).isEqualTo(36.0);
        assertThat(r.get("daysInMilk").asInt()).isEqualTo(84);
        assertThat(r.get("stage").asString()).isEqualTo("EARLY");
        assertThat(r.get("explain").asString()).contains("18.5 L × 0.4");
        assertThat(r.get("rule").get("effectiveFrom") == null || r.get("rule").get("effectiveFrom").isNull()).isTrue();

        // Owner picks items for milking cows from today; a worker can't
        String rule = "{\"effectiveFrom\":\"" + today + "\",\"params\":{\"green\":[{\"itemId\":" + velimasal + ",\"sharePct\":40},"
                + "{\"itemId\":" + napier + ",\"sharePct\":60}],\"dry\":[{\"itemId\":" + straw + ",\"sharePct\":100}],\"concItemId\":"
                + mix + "}}";
        call(put(base() + "/feed/ration-rules/MILKING"), worker, rule, 403);
        call(put(base() + "/feed/ration-rules/MILKING"), owner, rule.replace("\"sharePct\":60", "\"sharePct\":50"), 400);
        call(put(base() + "/feed/ration-rules/MILKING"), owner, rule, 200);
        // A rule from tomorrow: early-lactation DMI 4 % of body weight
        call(put(base() + "/feed/ration-rules/MILKING"), owner, "{\"effectiveFrom\":\"" + today.plusDays(1)
                + "\",\"params\":{\"dmiEarlyPct\":4}}", 200);
        JsonNode rules = call(get(base() + "/feed/ration-rules"), worker, null, 200);
        JsonNode milking = rules.get(0);
        assertThat(milking.get("category").asString()).isEqualTo("MILKING");
        assertThat(milking.get("current").get("effectiveFrom").asString()).isEqualTo(today.toString());
        assertThat(milking.get("current").get("params").get("dmiEarlyPct").asDouble()).isEqualTo(3.75);
        assertThat(milking.get("upcoming").size()).isEqualTo(1);
        assertThat(call(get(base() + "/feed/ration?animalId=" + cow + "&date=" + today.plusDays(1)), owner, null, 200)
                .get("totals").get("targetKg").asDouble()).isEqualTo(18.0);

        r = call(get(base() + "/feed/ration?animalId=" + cow), owner, null, 200);
        assertThat(r.get("lines").toString()).contains("Velimasal", "CO5 Napier", "Paddy straw", "Concentrate mix");
        assertThat(r.get("lines").get(0).get("kg").asDouble()).isEqualTo(9.76);  // green DM 7.316 × 40 % ÷ 30 %

        // Feeding sheet for the whole farm, morning: half the day plan
        JsonNode sheet = call(get(base() + "/feed/feeding-sheet?date=" + today + "&session=AM"), worker, null, 200);
        assertThat(sheet.get("animals").asInt()).isEqualTo(2);
        assertThat(sheet.get("saved").asBoolean()).isFalse();
        JsonNode first = sheet.get("lines").get(0);
        assertThat(first.get("section").asString()).isEqualTo("GREEN");
        double velPlanned = 0;
        for (JsonNode l : sheet.get("lines")) {
            if (l.get("item").get("id").asLong() == velimasal) {
                velPlanned = l.get("plannedKg").asDouble();
            }
        }
        assertThat(velPlanned).isEqualTo(4.9);
        call(get(base() + "/feed/feeding-sheet?target=GROUP"), worker, null, 400);

        call(post(base() + "/feed/items/" + velimasal + "/stock"), owner, "{\"type\":\"HARVEST\",\"date\":\"" + today
                + "\",\"quantity\":20}", 200);
        String save = "{\"date\":\"" + today + "\",\"session\":\"AM\",\"time\":\"06:10\",\"target\":\"FARM\",\"lines\":[{\"itemId\":"
                + velimasal + ",\"kg\":3},{\"itemId\":" + napier + ",\"kg\":7.6}]}";
        JsonNode saved = call(put(base() + "/feed/feeding-sheet"), worker, save, 200);
        assertThat(saved.get("saved").asBoolean()).isTrue();
        assertThat(saved.get("actualKg").asDouble()).isEqualTo(10.6);
        assertThat(saved.get("warnings").toString()).contains("Velimasal 1.9 kg under plan");
        // Saving again replaces the session (stock used once); lines left out are removed
        call(put(base() + "/feed/feeding-sheet"), worker, save.replace(",{\"itemId\":" + napier + ",\"kg\":7.6}", ""), 200);
        assertThat(call(get(base() + "/feed/items/" + velimasal), owner, null, 200).get("stock").get("stock").asDouble()).isEqualTo(17.0);
        assertThat(call(get(base() + "/feed/feeding-sheet?date=" + today + "&session=AM"), worker, null, 200)
                .get("actualKg").asDouble()).isEqualTo(3.0);

        JsonNode ov = call(get(base() + "/feed/overview"), worker, null, 200);
        assertThat(ov.get("sessions").get("AM").get("logged").asBoolean()).isTrue();
        assertThat(ov.get("sessions").get("AM").get("kg").asDouble()).isEqualTo(3.0);
        assertThat(ov.get("sessions").get("AM").get("time").asString()).isEqualTo("06:10");
        assertThat(ov.get("sessions").get("AM").get("by").asString()).isEqualTo("Murugan");
        assertThat(ov.get("sessions").get("PM").get("logged").asBoolean()).isFalse();
        assertThat(ov.get("needPerDay").get("concentrate").asDouble()).isEqualTo(11.9);   // 8.9 + dry cow 420 × 2 % × 30 % ÷ 80 % = 3.15, capped at 3
        assertThat(ov.get("plannedCostPerDay")).isNull();
        JsonNode vel = null;
        for (JsonNode s : ov.get("items")) {
            if (s.get("item").get("id").asLong() == velimasal) {
                vel = s;
            }
        }
        assertThat(vel.get("needPerDay").asDouble()).isEqualTo(9.76);
        assertThat(vel.get("daysLeft").asDouble()).isEqualTo(1.7);
        assertThat(vel.get("status").asString()).isEqualTo("REORDER");
        assertThat(ov.get("reorderCount").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode summary = call(get(base() + "/feed/ration/summary"), owner, null, 200);
        assertThat(summary.get("categories").get(0).get("category").asString()).isEqualTo("MILKING");
        assertThat(summary.get("categories").get(1).get("animals").asInt()).isEqualTo(1);

        JsonNode need = call(get(base() + "/feed/need?days=7"), owner, null, 200);
        assertThat(need.get("animals").asInt()).isEqualTo(2);
        assertThat(need.get("rows").toString()).contains("Velimasal");
        assertThat(need.get("shortfalls").toString()).contains("Velimasal");
    }
}
