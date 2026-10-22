package com.dairyfarm.finance;

import static com.dairyfarm.support.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dairyfarm.support.Api;
import com.dairyfarm.support.Daily;
import com.dairyfarm.support.IntegrationTest;
import com.dairyfarm.support.TestTokens.TestUser;

import tools.jackson.databind.JsonNode;

class MoneyControllerTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Bharath");
    private final TestUser worker = TestUser.random("Murugan");
    private long farmId;
    private LocalDate today;
    private long supplier;
    private long buyer;
    private long tech;

    @BeforeEach
    void setUp() throws Exception {
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
        Api.addWorker(jdbc, farmId, worker);
        today = Daily.today();
        supplier = Daily.contact(mvc, json, owner, farmId, "Feed Store", "\"SUPPLIER\"", "");
        buyer = Daily.contact(mvc, json, owner, farmId, "Aavin", "\"BUYER\"", "");
        tech = Daily.contact(mvc, json, owner, farmId, "Ravi", "\"AI_TECH\"", "");
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

    private JsonNode entries(String category) throws Exception {
        return call(get(base() + "/finance/entries?category=" + category), owner, null, 200).get("items");
    }

    private long cow(String tag, String extra) throws Exception {
        return Daily.animal(mvc, json, owner, farmId, tag, "MILKING", extra);
    }

    private long feedItem(String name, String extra) throws Exception {
        return call(post(base() + "/feed/items"), owner, "{\"name\":\"" + name + "\",\"category\":\"CONCENTRATE_INGREDIENT\""
                + extra + "}", 201).get("id").asLong();
    }

    @Test
    void automaticEntriesFollowTheirSources() throws Exception {
        long lakshmi = cow("VD-001", "\"name\":\"Lakshmi\",\"purchasePrice\":50000,\"acquisitionDate\":\"" + today.minusDays(30) + "\"");
        JsonNode bought = entries("ANIMAL_PURCHASE");
        assertThat(bought).hasSize(1);
        assertThat(bought.get(0).get("amount").asDouble()).isEqualTo(50000);
        assertThat(bought.get(0).get("source").get("type").asString()).isEqualTo("ANIMAL");
        assertThat(bought.get(0).get("source").get("id").asLong()).isEqualTo(lakshmi);
        assertThat(bought.get(0).get("auto").asBoolean()).isTrue();

        // milk sale: amount, pending → paid written back, delete
        String sale = "{\"date\":\"" + today + "\",\"session\":\"AM\",\"quantity\":20,\"soldQty\":%s,\"rate\":40,"
                + "\"buyerContactId\":" + buyer + ",\"paid\":false}";
        long saleId = call(put(base() + "/milk/sales"), owner, sale.formatted("10"), 200).get("id").asLong();
        JsonNode milk = entries("MILK_SALES").get(0);
        assertThat(milk.get("amount").asDouble()).isEqualTo(400);
        assertThat(milk.get("status").asString()).isEqualTo("PENDING");
        assertThat(milk.get("source").get("type").asString()).isEqualTo("PRODUCTION");
        assertThat(milk.get("source").get("id").asLong()).isEqualTo(saleId);
        assertThat(milk.get("source").get("label").asString()).contains("10 L × ₹40.00");
        assertThat(milk.get("counterparty").asString()).isEqualTo("Aavin");
        call(put(base() + "/milk/sales"), owner, sale.formatted("12"), 200);
        milk = entries("MILK_SALES").get(0);
        assertThat(milk.get("amount").asDouble()).isEqualTo(480);
        call(put(base() + "/finance/entries/" + milk.get("id").asLong()), owner,
                "{\"status\":\"PAID\",\"paymentMethod\":\"UPI\",\"notes\":\"settled\",\"amount\":1}", 200);
        milk = entries("MILK_SALES").get(0);
        assertThat(milk.get("status").asString()).isEqualTo("PAID");
        assertThat(milk.get("amount").asDouble()).isEqualTo(480);
        assertThat(jdbc.queryForObject("select paid from production_record where id = ?", Boolean.class, saleId)).isTrue();
        call(delete(base() + "/finance/entries/" + milk.get("id").asLong()), owner, null, 409);
        call(delete(base() + "/milk/sales/" + saleId), owner, null, 204);
        assertThat(entries("MILK_SALES")).isEmpty();

        // feed purchase on credit
        long gnc = feedItem("Groundnut cake", "");
        call(post(base() + "/feed/purchases"), owner, "{\"itemId\":" + gnc + ",\"date\":\"" + today
                + "\",\"quantity\":50,\"costPerUnit\":44,\"supplierContactId\":" + supplier + ",\"paid\":false}", 201);
        JsonNode feed = entries("FEED").get(0);
        assertThat(feed.get("amount").asDouble()).isEqualTo(2200);
        assertThat(feed.get("status").asString()).isEqualTo("PENDING");
        assertThat(feed.get("source").get("type").asString()).isEqualTo("ITEM_TXN");
        assertThat(feed.get("source").get("id").asLong()).isEqualTo(gnc);
        assertThat(feed.get("source").get("label").asString()).isEqualTo("Groundnut cake 50 kg");

        // health cost: allocated to the cow, follows edits and delete
        String health = "{\"animalId\":" + lakshmi + ",\"date\":\"" + today + "\",\"type\":\"TREATMENT\",\"diagnosis\":\"Mastitis\",\"cost\":%s}";
        long rec = call(post(base() + "/health/records"), owner, health.formatted("650"), 201).get("id").asLong();
        JsonNode vet = entries("VET").get(0);
        assertThat(vet.get("amount").asDouble()).isEqualTo(650);
        assertThat(vet.get("alloc").get("kind").asString()).isEqualTo("ANIMAL");
        assertThat(vet.get("alloc").get("animal").get("id").asLong()).isEqualTo(lakshmi);
        assertThat(vet.get("source").get("label").asString()).contains("VD-001 Lakshmi").contains("Mastitis");
        call(put(base() + "/health/records/" + rec), owner, health.formatted("700"), 200);
        assertThat(entries("VET").get(0).get("amount").asDouble()).isEqualTo(700);
        call(delete(base() + "/health/records/" + rec), owner, null, 204);
        assertThat(entries("VET")).isEmpty();

        // semen stock and the technician fee
        long semen = call(post(base() + "/semen"), owner, "{\"bullName\":\"Bull A\",\"bullCode\":\"HF-1\",\"breed\":\"HF\","
                + "\"costPerStraw\":450,\"straws\":5,\"expiryDate\":\"" + today.plusYears(1) + "\"}", 201).get("id").asLong();
        assertThat(entries("SEMEN").get(0).get("amount").asDouble()).isEqualTo(2250);
        long svc = call(post(base() + "/breeding/services"), owner, "{\"animalId\":" + lakshmi + ",\"date\":\"" + today
                + "\",\"method\":\"AI\",\"semenItemId\":" + semen + ",\"technicianContactId\":" + tech
                + ",\"technicianFee\":300,\"feePaid\":false}", 201).get("id").asLong();
        JsonNode fee = entries("BREEDING").get(0);
        assertThat(fee.get("amount").asDouble()).isEqualTo(300);
        assertThat(fee.get("status").asString()).isEqualTo("PENDING");
        assertThat(fee.get("source").get("part").asString()).isEqualTo("TECH_FEE");
        assertThat(fee.get("source").get("id").asLong()).isEqualTo(svc);
        call(put(base() + "/finance/entries/" + fee.get("id").asLong()), owner, "{\"status\":\"PAID\"}", 200);
        assertThat(jdbc.queryForObject("select fee_paid from breeding_record where id = ?", Boolean.class, svc)).isTrue();

        // sold → income; deleting the animal removes its entries
        call(patch(base() + "/animals/" + lakshmi + "/status"), owner,
                "{\"status\":\"SOLD\",\"date\":\"" + today + "\",\"price\":60000,\"party\":\"Ravi\"}", 200);
        JsonNode sold = entries("ANIMAL_SALES").get(0);
        assertThat(sold.get("amount").asDouble()).isEqualTo(60000);
        assertThat(sold.get("counterparty").asString()).isEqualTo("Ravi");
        call(delete(base() + "/animals/" + lakshmi), owner, null, 204);
        assertThat(entries("ANIMAL_SALES")).isEmpty();
        assertThat(entries("ANIMAL_PURCHASE")).isEmpty();
        assertThat(entries("BREEDING")).isEmpty();
        assertThat(entries("FEED")).hasSize(1);
    }

    @Test
    void manualEntriesSummaryAndPending() throws Exception {
        call(post(base() + "/finance/entries"), owner, "{\"category\":\"FEED\",\"direction\":\"INCOME\",\"amount\":10,\"date\":\""
                + today + "\"}", 400);
        call(post(base() + "/finance/entries"), owner, "{\"category\":\"FEED\",\"amount\":10,\"date\":\"" + today.plusDays(1) + "\"}", 400);
        JsonNode util = call(post(base() + "/finance/entries"), owner, "{\"category\":\"UTILITIES\",\"amount\":2900,\"date\":\""
                + today + "\",\"paymentMethod\":\"UPI\",\"counterparty\":\"TNEB\"}", 201);
        assertThat(util.get("direction").asString()).isEqualTo("EXPENSE");
        assertThat(util.get("auto").asBoolean()).isFalse();
        assertThat(util.has("source") && !util.get("source").isNull()).isFalse();
        call(post(base() + "/finance/entries"), owner, "{\"category\":\"MANURE_SALES\",\"amount\":4200,\"date\":\""
                + today + "\",\"status\":\"PENDING\"}", 201);
        call(post(base() + "/finance/entries"), owner, "{\"category\":\"MILK_SALES\",\"amount\":10000,\"date\":\"" + today + "\"}", 201);
        long id = util.get("id").asLong();
        call(put(base() + "/finance/entries/" + id), owner, "{\"category\":\"UTILITIES\",\"amount\":3000,\"date\":\"" + today + "\"}", 200);

        JsonNode s = call(get(base() + "/finance/summary?from=" + today + "&to=" + today), owner, null, 200);
        assertThat(s.get("income").asDouble()).isEqualTo(14200);
        assertThat(s.get("expense").asDouble()).isEqualTo(3000);
        assertThat(s.get("net").asDouble()).isEqualTo(11200);
        assertThat(s.get("marginPct").asDouble()).isEqualTo(78.9);
        assertThat(s.get("nearAlert").asBoolean()).isFalse();
        assertThat(s.get("pending").get("count").asInt()).isEqualTo(1);
        assertThat(s.get("pending").get("amount").asDouble()).isEqualTo(4200);
        assertThat(s.get("incomeByCategory").get(0).get("category").asString()).isEqualTo("MILK_SALES");
        call(delete(base() + "/finance/entries/" + id), owner, null, 204);
    }

    @Test
    void profitPerAnimal() throws Exception {
        long cow = cow("VD-001", "\"name\":\"Lakshmi\",\"purchasePrice\":50000,\"acquisitionDate\":\"" + today.minusDays(30)
                + "\",\"lastCalvingDate\":\"" + today.minusDays(60) + "\"");
        call(put(base() + "/milk/cows/" + cow + "/days/" + today), owner, "{\"am\":10,\"pm\":8}", 200);
        call(put(base() + "/milk/sales"), owner, "{\"date\":\"" + today + "\",\"session\":\"DAY\",\"soldQty\":18,\"rate\":40,\"paid\":true}", 200);
        long gnc = feedItem("Groundnut cake", "");
        call(post(base() + "/feed/purchases"), owner, "{\"itemId\":" + gnc + ",\"date\":\"" + today
                + "\",\"quantity\":25,\"costPerUnit\":40}", 201);
        call(post(base() + "/health/records"), owner, "{\"animalId\":" + cow + ",\"date\":\"" + today
                + "\",\"type\":\"TREATMENT\",\"cost\":650}", 201);

        JsonNode p = call(get(base() + "/finance/pnl/animals/" + cow), owner, null, 200);
        assertThat(p.get("milkLitres").asDouble()).isEqualTo(18);
        assertThat(line(p, "MILK")).isEqualTo(720);
        assertThat(line(p, "FEED")).isEqualTo(-1000);
        assertThat(line(p, "HEALTH")).isEqualTo(-650);
        assertThat(line(p, "PURCHASE")).isEqualTo(-50000);
        assertThat(p.get("net").asDouble()).isEqualTo(-50930);
        assertThat(p.get("rank").asInt()).isEqualTo(1);
        assertThat(p.get("onFarmDays").asLong()).isEqualTo(31);

        JsonNode all = call(get(base() + "/finance/pnl"), owner, null, 200);
        assertThat(all.get("animals").get(0).get("animal").get("tag").asString()).isEqualTo("VD-001");
        assertThat(all.get("farm").get("income").asDouble()).isEqualTo(720);
    }

    private static double line(JsonNode pnl, String key) {
        for (JsonNode l : pnl.get("lines")) {
            if (l.get("key").asString().equals(key)) {
                return l.get("amount").asDouble();
            }
        }
        return 0;
    }

    @Test
    void attendanceWagesAndAdvances() throws Exception {
        long murugan = call(post(base() + "/members"), owner,
                "{\"name\":\"Murugan K\",\"role\":\"WORKER\",\"wageType\":\"DAILY\",\"wageRate\":500}", 201).get("id").asLong();
        long selvi = call(post(base() + "/members"), owner,
                "{\"name\":\"Selvi\",\"role\":\"WORKER\",\"wageType\":\"MONTHLY\",\"wageRate\":12000}", 201).get("id").asLong();
        LocalDate mon = today.with(DayOfWeek.MONDAY).minusWeeks(2);
        call(put(base() + "/labour/attendance"), owner, "{\"entries\":["
                + "{\"memberId\":" + murugan + ",\"date\":\"" + mon + "\",\"status\":\"PRESENT\",\"hours\":8},"
                + "{\"memberId\":" + murugan + ",\"date\":\"" + mon.plusDays(1) + "\",\"status\":\"PRESENT\",\"hours\":8},"
                + "{\"memberId\":" + murugan + ",\"date\":\"" + mon.plusDays(2) + "\",\"status\":\"HALF\",\"hours\":4},"
                // The week screen also sends blank days, future ones included: nothing to save, no error.
                + "{\"memberId\":" + murugan + ",\"date\":\"" + today.plusDays(3) + "\",\"status\":null,\"hours\":null}]}", 200);
        call(put(base() + "/labour/attendance"), owner, "{\"entries\":[{\"memberId\":" + murugan + ",\"date\":\""
                + today.plusDays(1) + "\",\"status\":\"PRESENT\",\"hours\":8}]}", 400);
        JsonNode week = call(get(base() + "/labour/attendance?weekOf=" + mon.plusDays(3)), owner, null, 200);
        JsonNode row = week.get("rows").get(0).get("member").get("id").asLong() == murugan ? week.get("rows").get(0) : week.get("rows").get(1);
        assertThat(row.get("presentDays").asDouble()).isEqualTo(2.5);
        assertThat(row.get("amount").asDouble()).isEqualTo(1250);
        assertThat(row.get("days").get(2).get("status").asString()).isEqualTo("HALF");

        String period = "from=" + mon + "&to=" + mon.plusDays(6);
        JsonNode due = call(get(base() + "/labour/workers/" + murugan + "/due?" + period), owner, null, 200);
        assertThat(due.get("gross").asDouble()).isEqualTo(1250);
        // Added to the app today, but days marked before that are still due.
        JsonNode firstDue = call(get(base() + "/labour/workers/" + murugan + "/due"), owner, null, 200);
        assertThat(firstDue.get("from").asString()).isEqualTo(mon.toString());
        assertThat(firstDue.get("gross").asDouble()).isEqualTo(1250);
        call(post(base() + "/labour/payments"), owner, "{\"memberId\":" + murugan + ",\"amount\":300,\"advance\":true,\"paymentMethod\":\"CASH\"}", 201);
        due = call(get(base() + "/labour/workers/" + murugan + "/due?" + period), owner, null, 200);
        assertThat(due.get("advances").asDouble()).isEqualTo(300);
        assertThat(due.get("amountDue").asDouble()).isEqualTo(950);
        JsonNode paid = call(post(base() + "/labour/payments"), owner, "{\"memberId\":" + murugan + ",\"periodFrom\":\"" + mon
                + "\",\"periodTo\":\"" + mon.plusDays(6) + "\",\"amount\":950,\"paymentMethod\":\"CASH\"}", 201);
        assertThat(paid.get("category").asString()).isEqualTo("LABOUR");
        assertThat(paid.get("source").get("type").asString()).isEqualTo("LABOUR");
        assertThat(paid.get("source").get("id").asLong()).isEqualTo(murugan);
        due = call(get(base() + "/labour/workers/" + murugan + "/due?" + period), owner, null, 200);
        assertThat(due.get("advances").asDouble()).isEqualTo(0);
        JsonNode defaultDue = call(get(base() + "/labour/workers/" + murugan + "/due"), owner, null, 200);
        assertThat(defaultDue.get("from").asString()).isEqualTo(mon.plusDays(7).toString());

        // monthly: absences beyond the 4 leave days a month are deducted
        YearMonth prev = YearMonth.from(today).minusMonths(1);
        StringBuilder marks = new StringBuilder("{\"entries\":[");
        for (int d = 1; d <= 6; d++) {
            marks.append(d > 1 ? "," : "").append("{\"memberId\":").append(selvi).append(",\"date\":\"").append(prev.atDay(d))
                    .append("\",\"status\":\"ABSENT\"}");
        }
        call(put(base() + "/labour/attendance"), owner, marks.append("]}").toString(), 200);
        JsonNode monthly = call(get(base() + "/labour/workers/" + selvi + "/due?from=" + prev.atDay(1) + "&to=" + prev.atEndOfMonth()),
                owner, null, 200);
        double daily = BigDecimal.valueOf(12000).divide(BigDecimal.valueOf(prev.lengthOfMonth()), 4, RoundingMode.HALF_UP).doubleValue();
        assertThat(monthly.get("absentDays").asDouble()).isEqualTo(6);
        assertThat(monthly.get("gross").asDouble()).isCloseTo(12000 - 2 * daily, org.assertj.core.data.Offset.offset(0.05));

        JsonNode workers = call(get(base() + "/labour/workers"), owner, null, 200);
        assertThat(workers.get("totals").get("workers").asInt()).isEqualTo(2);
        assertThat(workers.get("totals").get("paidThisMonth").asDouble()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void equipmentServiceDatesAndExpense() throws Exception {
        long mechanic = Daily.contact(mvc, json, owner, farmId, "Kumar Motors", "\"SUPPLIER\"", "");
        JsonNode m = call(post(base() + "/equipment"), owner, "{\"name\":\"Milking machine\",\"serviceEveryDays\":90,\"purchaseDate\":\""
                + today.minusDays(100) + "\"}", 201);
        long id = m.get("id").asLong();
        assertThat(m.get("status").asString()).isEqualTo("OVERDUE");
        assertThat(m.get("daysTo").asLong()).isEqualTo(-10);
        assertThat(m.get("reminderDays").asInt()).isEqualTo(7);
        call(post(base() + "/equipment/" + id + "/services"), owner, "{\"date\":\"" + today + "\",\"cost\":1800,\"mechanicContactId\":" + buyer + "}", 400);
        JsonNode after = call(post(base() + "/equipment/" + id + "/services"), owner, "{\"date\":\"" + today
                + "\",\"workDone\":\"LINER_CHANGE\",\"cost\":1800,\"paymentMethod\":\"UPI\",\"mechanicContactId\":" + mechanic + "}", 201);
        assertThat(after.get("status").asString()).isEqualTo("OK");
        assertThat(after.get("lastService").asString()).isEqualTo(today.toString());
        assertThat(after.get("nextService").asString()).isEqualTo(today.plusDays(90).toString());
        assertThat(call(get(base() + "/equipment/" + id), owner, null, 200).get("name").asString()).isEqualTo("Milking machine");
        assertThat(call(get(base() + "/equipment/" + id + "/services"), owner, null, 200).get(0).get("workDone").asString())
                .isEqualTo("LINER_CHANGE");
        JsonNode e = entries("EQUIPMENT").get(0);
        assertThat(e.get("amount").asDouble()).isEqualTo(1800);
        assertThat(e.get("source").get("type").asString()).isEqualTo("EQUIPMENT_SERVICE");
        assertThat(e.get("source").get("id").asLong()).isEqualTo(id);
        assertThat(e.get("counterparty").asString()).isEqualTo("Kumar Motors");
        JsonNode list = call(get(base() + "/equipment"), owner, null, 200);
        assertThat(list.get("counts").get("items").asInt()).isEqualTo(1);
        call(delete(base() + "/equipment/" + id), owner, null, 409);
    }

    @Test
    void procurementReceiveAddsStockAndExpense() throws Exception {
        long gnc = feedItem("Groundnut cake", ",\"reorderLevel\":40,\"supplierContactId\":" + supplier);
        call(post(base() + "/feed/purchases"), owner, "{\"itemId\":" + gnc + ",\"date\":\"" + today
                + "\",\"quantity\":10,\"costPerUnit\":40}", 201);
        JsonNode board = call(get(base() + "/procurement"), owner, null, 200);
        assertThat(board.get("suggestions").get(0).get("item").get("id").asLong()).isEqualTo(gnc);

        long note = call(post(base() + "/procurement"), owner, "{\"itemId\":" + gnc + ",\"quantity\":100,\"expectedPrice\":4000,\"neededBy\":\""
                + today.plusDays(3) + "\",\"source\":\"LOW_STOCK\"}", 201).get("id").asLong();
        board = call(get(base() + "/procurement"), owner, null, 200);
        assertThat(board.get("suggestions")).isEmpty();
        assertThat(board.get("counts").get("planned").asInt()).isEqualTo(1);
        JsonNode n = call(post(base() + "/procurement/" + note + "/order"), owner, "{}", 200);
        assertThat(n.get("status").asString()).isEqualTo("ORDERED");
        assertThat(n.get("supplier").get("name").asString()).isEqualTo("Feed Store");
        n = call(post(base() + "/procurement/" + note + "/receive"), owner, "{\"totalAmount\":4200,\"paid\":false}", 200);
        assertThat(n.get("status").asString()).isEqualTo("RECEIVED");
        assertThat(call(get(base() + "/feed/items/" + gnc), owner, null, 200).get("stock").get("stock").asDouble()).isEqualTo(110);
        JsonNode feed = entries("FEED");
        assertThat(feed).hasSize(2);
        assertThat(feed.get(0).get("amount").asDouble() + feed.get(1).get("amount").asDouble()).isEqualTo(4600);
        call(post(base() + "/procurement/" + note + "/receive"), owner, "{}", 400);

        long liners = call(post(base() + "/procurement"), owner, "{\"title\":\"Milking liners\",\"quantity\":1,\"unit\":\"NOS\",\"expectedPrice\":1200}",
                201).get("id").asLong();
        call(post(base() + "/procurement/" + liners + "/receive"), owner, "{}", 200);
        JsonNode other = entries("OTHER_EXPENSE").get(0);
        assertThat(other.get("amount").asDouble()).isEqualTo(1200);
        assertThat(other.get("auto").asBoolean()).isFalse();
    }

    @Test
    void workersCannotSeeMoney() throws Exception {
        call(get(base() + "/finance/summary"), worker, null, 403);
        call(get(base() + "/finance/entries"), worker, null, 403);
        call(get(base() + "/finance/pnl"), worker, null, 403);
        call(get(base() + "/labour/workers"), worker, null, 403);
        call(get(base() + "/equipment"), worker, null, 403);
        call(get(base() + "/procurement"), worker, null, 403);
    }
}
