package com.dairyfarm.home;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.alert.AlertService;
import com.dairyfarm.alert.AlertViews.Alert;
import com.dairyfarm.alert.AlertViews.AlertCategory;
import com.dairyfarm.alert.AlertViews.MemberRef;
import com.dairyfarm.animal.AnimalEnums.Category;
import com.dairyfarm.breeding.BreedingQueryService;
import com.dairyfarm.breeding.BreedingViews;
import com.dairyfarm.breeding.BreedingViews.CowStatus;
import com.dairyfarm.common.ApiException;
import com.dairyfarm.farm.Farm;
import com.dairyfarm.farm.FarmRepository;
import com.dairyfarm.farm.FarmService;
import com.dairyfarm.farm.FarmSettings;
import com.dairyfarm.feed.FeedService;
import com.dairyfarm.feed.FeedViews.SessionStatus;
import com.dairyfarm.finance.FinanceService;
import com.dairyfarm.home.HomeViews.AnimalRef;
import com.dairyfarm.home.HomeViews.Barn;
import com.dairyfarm.home.HomeViews.BarnLevel;
import com.dairyfarm.home.HomeViews.BarnRequest;
import com.dairyfarm.home.HomeViews.Calving;
import com.dairyfarm.home.HomeViews.Checklist;
import com.dairyfarm.home.HomeViews.FeedingReminder;
import com.dairyfarm.home.HomeViews.Home;
import com.dairyfarm.home.HomeViews.HomeTask;
import com.dairyfarm.home.HomeViews.Link;
import com.dairyfarm.home.HomeViews.Producer;
import com.dairyfarm.home.HomeViews.Progress;
import com.dairyfarm.home.HomeViews.Task;
import com.dairyfarm.home.HomeViews.TaskRequest;
import com.dairyfarm.home.HomeViews.TaskStatus;
import com.dairyfarm.home.HomeViews.Tiles;
import com.dairyfarm.milk.MilkService;
import com.dairyfarm.milk.MilkViews.DaySummary;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.record.FarmEvent;
import com.dairyfarm.record.FarmEvent.EventType;
import com.dairyfarm.record.FarmEventRepository;
import com.dairyfarm.security.FarmContext;

/** The home screen, the daily checklist and barn heat-stress readings. */
@Service
public class HomeService {

    static final List<String> BUILT_IN = List.of("MILK_AM", "MILK_PM", "FEED_AM", "FEED_PM", "FEED_STOCK", "HEAT_STRESS");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final FarmRepository farms;
    private final MilkService milk;
    private final FeedService feed;
    private final AlertService alerts;
    private final FinanceService finance;
    private final BreedingQueryService breeding;
    private final FarmEventRepository events;
    private final JdbcClient jdbc;
    private final FarmContext ctx;

    public HomeService(FarmRepository farms, MilkService milk, FeedService feed, AlertService alerts, FinanceService finance,
                       BreedingQueryService breeding, FarmEventRepository events, JdbcClient jdbc, FarmContext ctx) {
        this.farms = farms;
        this.milk = milk;
        this.feed = feed;
        this.alerts = alerts;
        this.finance = finance;
        this.breeding = breeding;
        this.events = events;
        this.jdbc = jdbc;
        this.ctx = ctx;
    }

    // ---------- home ----------

    @Transactional
    public Home home(Long farmId, boolean owner) {
        LocalDate today = DailyDates.today();
        Farm farm = farm(farmId);
        FarmSettings settings = farm.getSettings();
        DaySummary day = milk.day(farmId, today);
        List<Alert> open = alerts.visibleOpen(farmId, owner);
        int overdue = (int) open.stream().filter(a -> a.overdueDays() != null && a.overdueDays() > 0).count();

        boolean pmLogged = day.pm() != null && day.pm().cows() > 0;
        boolean amLogged = day.am() != null && day.am().cows() > 0;
        BigDecimal change = pmLogged ? day.change().totalPct() : amLogged ? day.change().amPct() : null;
        String note = pmLogged ? "vs yesterday" : amLogged ? "vs yesterday (AM only)" : null;
        int[] herd = herdCounts(farmId);
        BigDecimal margin = null;
        Integer marginAlert = null;
        if (owner) {
            margin = finance.summary(farmId, today.withDayOfMonth(1), today).marginPct();
            marginAlert = settings.marginAlertPct();
        }
        Tiles tiles = new Tiles(day.totalLitres(), change, note, herd[0], herd[1], herd[2], open.size(), overdue, margin, marginAlert);

        Checklist cl = checklist(farmId, today, owner);
        List<HomeTask> tasks = new ArrayList<>();
        Map<String, Task> byKey = new HashMap<>();
        cl.builtIn().forEach(t -> byKey.put(t.key(), t));
        Map<String, SessionStatus> feedSessions = feed.overview(farmId, today, owner).sessions();
        tasks.add(sessionTask("MILK_AM", "Morning milking", byKey.get("MILK_AM"), settings.morningMilking(), milkCount(day, true),
                new Link("MILK_AM", null)));
        tasks.add(sessionTask("MILK_PM", "Evening milking", byKey.get("MILK_PM"), settings.eveningMilking(), milkCount(day, false),
                new Link("MILK_PM", null)));
        tasks.add(sessionTask("FEED_AM", "Feeding AM", byKey.get("FEED_AM"), settings.morningFeeding(), null,
                new Link("FEED_AM", null)));
        tasks.add(sessionTask("FEED_PM", "Feeding PM", byKey.get("FEED_PM"), settings.eveningFeeding(), null,
                new Link("FEED_PM", null)));
        open.stream().filter(a -> !a.dueDate().isAfter(today))
                .sorted(Comparator.comparing((Alert a) -> a.severity().ordinal()).thenComparing(Alert::dueDate))
                .limit(3)
                .forEach(a -> tasks.add(new HomeTask("ALERT:" + a.id(),
                        a.title() + (a.animal() == null ? "" : " · " + tagName(a.animal().tag(), a.animal().name())),
                        a.overdueDays() != null && a.overdueDays() > 0 ? TaskStatus.LATE : TaskStatus.DUE,
                        a.overdueDays() != null && a.overdueDays() > 0 ? "Overdue " + a.overdueDays() + " d" : "Today", a.dueTime(),
                        new Link("ALERT", a.id()))));

        List<Producer> top = owner ? topProducers(farmId, today) : List.of();
        List<Calving> calvings = calvings(farmId, today);
        List<Alert> health = owner ? List.of() : open.stream().filter(a -> a.category() == AlertCategory.HEALTH).limit(5).toList();
        List<FeedingReminder> feeding = new ArrayList<>();
        if (!owner) {
            for (String k : List.of("FEED_AM", "FEED_PM")) {
                HomeTask t = tasks.stream().filter(x -> x.key().equals(k)).findFirst().orElseThrow();
                feeding.add(new FeedingReminder(k.equals("FEED_AM") ? "Feed AM" : "Feed PM", t.detail(), t.status()));
            }
            open.stream().filter(a -> a.category() == AlertCategory.CALF).limit(3)
                    .forEach(a -> feeding.add(new FeedingReminder(a.title(), a.detail(),
                            a.overdueDays() != null && a.overdueDays() > 0 ? TaskStatus.LATE : TaskStatus.DUE)));
        }
        String greeting = greeting(farmId);
        return new Home(owner ? "OWNER" : "WORKER", greeting, today, tiles, tasks, new Progress(cl.done(), cl.total()),
                latestBarn(farmId, today), top, calvings, health, feeding);
    }

    private static String milkCount(DaySummary day, boolean am) {
        var s = am ? day.am() : day.pm();
        int cows = s == null ? 0 : s.cows();
        return cows == 0 ? null : cows + " of " + day.milkingCows() + " logged";
    }

    private HomeTask sessionTask(String key, String title, Task t, LocalTime planned, String count, Link link) {
        LocalTime now = LocalTime.now(FarmService.FARM_ZONE);
        if (t != null && t.done()) {
            LocalTime at = t.doneAt() == null ? null
                    : LocalDateTime.ofInstant(t.doneAt(), FarmService.FARM_ZONE).toLocalTime().withSecond(0).withNano(0);
            String by = t.doneBy() == null || t.doneBy().name() == null ? "" : "done by " + t.doneBy().name();
            String detail = count != null ? count : by.isEmpty() ? "done" : by;
            return new HomeTask(key, title, TaskStatus.DONE, detail, at, link);
        }
        TaskStatus st = now.isBefore(planned) ? TaskStatus.PENDING
                : now.isBefore(planned.plusHours(3)) ? TaskStatus.DUE : TaskStatus.LATE;
        String detail = st == TaskStatus.PENDING ? "due " + planned.format(TIME) : "not logged";
        return new HomeTask(key, title, st, detail, planned, link);
    }

    private int[] herdCounts(Long farmId) {
        return jdbc.sql("""
                select count(*) filter (where category = 'MILKING' and sex = 'FEMALE'),
                       count(*) filter (where category = 'DRY'), count(*)
                from animal where farm_id = :f and status = 'ACTIVE'""")
                .param("f", farmId).query((rs, i) -> new int[] {rs.getInt(1), rs.getInt(2), rs.getInt(3)}).single();
    }

    private List<Producer> topProducers(Long farmId, LocalDate today) {
        return jdbc.sql("""
                select a.id, a.tag, a.name, sum(p.quantity) / nullif(count(distinct p.record_date), 0) as per_day
                from production_record p join animal a on a.id = p.animal_id
                where p.farm_id = :f and p.animal_id is not null and p.record_date between :from and :to and a.status = 'ACTIVE'
                group by a.id, a.tag, a.name order by per_day desc nulls last limit 3""")
                .param("f", farmId).param("from", today.minusDays(6)).param("to", today)
                .query((rs, i) -> new Producer(new AnimalRef(rs.getLong(1), rs.getString(2), rs.getString(3)),
                        rs.getBigDecimal(4) == null ? null : rs.getBigDecimal(4).setScale(1, RoundingMode.HALF_UP)))
                .list();
    }

    private List<Calving> calvings(Long farmId, LocalDate today) {
        List<Calving> out = new ArrayList<>();
        for (CowStatus c : breeding.allStatuses(farmId, false)) {
            BreedingViews.Status st = c.status();
            if (c.expectedDueDate() == null || !(st == BreedingViews.Status.PREGNANT || st == BreedingViews.Status.DRY_PREGNANT
                    || st == BreedingViews.Status.OVERDUE)) {
                continue;
            }
            if (c.expectedDueDate().isAfter(today.plusDays(30))) {
                continue;
            }
            String note = (c.breed() == null ? "" : c.breed() + " · ") + (c.category() == Category.DRY ? "dry" : "pregnant");
            if (c.category() == Category.MILKING && c.dryOffDate() != null && !c.dryOffDate().isBefore(today)) {
                note += " · dry-off first";
            }
            if (st == BreedingViews.Status.OVERDUE) {
                note += " · overdue";
            }
            out.add(new Calving(new AnimalRef(c.animal().id(), c.animal().tag(), c.animal().name()), c.expectedDueDate(),
                    ChronoUnit.DAYS.between(today, c.expectedDueDate()), note));
        }
        out.sort(Comparator.comparing(Calving::dueDate));
        return out;
    }

    private String greeting(Long farmId) {
        Long memberId = ctx.memberId();
        if (memberId == null) {
            return null;
        }
        return jdbc.sql("select name from farm_member where id = :id").param("id", memberId).query(String.class)
                .optional().orElse(null);
    }

    // ---------- checklist ----------

    @Transactional(readOnly = true)
    public Checklist checklist(Long farmId, LocalDate date, boolean owner) {
        Farm farm = farm(farmId);
        FarmSettings settings = farm.getSettings();
        Map<String, Object[]> ticks = new HashMap<>();
        jdbc.sql("""
                select t.task_key, t.ticked_at, t.member_id, m.name from checklist_tick t left join farm_member m on m.id = t.member_id
                where t.farm_id = :f and t.tick_date = :d""")
                .param("f", farmId).param("d", date)
                .query((rs, i) -> ticks.put(rs.getString(1), new Object[] {rs.getTimestamp(2).toInstant(),
                        rs.getObject(3) == null ? null : rs.getLong(3), rs.getString(4)}))
                .list();
        List<Task> built = new ArrayList<>();
        for (String session : List.of("AM", "PM")) {
            Object[] m = milkLogged(farmId, date, session);
            LocalTime from = session.equals("AM") ? settings.morningMilking() : settings.eveningMilking();
            built.add(task(session.equals("AM") ? "MILK_AM" : "MILK_PM", "Log " + (session.equals("AM") ? "morning" : "evening") + " milk",
                    m, ticks, "from " + from.format(TIME), true, new Link("MILK_" + session, null)));
        }
        Map<String, SessionStatus> fs = feed.overview(farmId, date, owner).sessions();
        for (String session : List.of("AM", "PM")) {
            SessionStatus s = fs.get(session);
            Object[] m = s != null && s.logged()
                    ? new Object[] {s.time() == null ? null : LocalDateTime.of(date, s.time()).atZone(FarmService.FARM_ZONE).toInstant(),
                    null, s.by()} : null;
            LocalTime due = session.equals("AM") ? settings.morningFeeding() : settings.eveningFeeding();
            built.add(task("FEED_" + session, "Feed " + session, m, ticks, "due " + due.format(TIME), true, new Link("FEED_" + session, null)));
        }
        long reorder = jdbc.sql("""
                select count(*) from item i where i.farm_id = :f and i.item_type = 'FEED' and i.active
                  and i.reorder_level is not null and coalesce((select sum(case when t.txn_type = 'USAGE' then -t.quantity else t.quantity end)
                    from item_txn t where t.item_id = i.id), 0) < i.reorder_level""")
                .param("f", farmId).query(Long.class).single();
        built.add(task("FEED_STOCK", "Verify feed stock", null, ticks,
                reorder == 0 ? "check bins and fodder" : reorder + " item(s) below reorder level", false, new Link("FEED_STOCK", null)));
        Barn barn = barnOn(farmId, date);
        Object[] heat = barn == null ? null : new Object[] {LocalDateTime.of(date, barn.time() == null ? LocalTime.NOON : barn.time())
                .atZone(FarmService.FARM_ZONE).toInstant(), null, null};
        Task heatTask = task("HEAT_STRESS", "Check heat stress", heat, ticks, "log a barn reading", true, new Link("HEAT_STRESS", null));
        if (barn != null) {
            heatTask = new Task(heatTask.key(), null, heatTask.name(), true, true, heatTask.doneBy(), heatTask.doneAt(),
                    "THI " + barn.thi().setScale(0, RoundingMode.HALF_UP) + " · " + barn.level().name().toLowerCase(Locale.ROOT)
                            + (barn.time() == null ? "" : " · " + barn.time().format(TIME)), true, true, null, heatTask.link());
        }
        built.add(heatTask);

        List<Task> custom = new ArrayList<>();
        jdbc.sql("""
                select id, name, worker_visible, repeat_daily from checklist_task where farm_id = :f and active
                  and (repeat_daily or task_date = :d) order by sort_order, id""")
                .param("f", farmId).param("d", date)
                .query((rs, i) -> {
                    boolean wv = rs.getBoolean(3);
                    if (owner || wv) {
                        String key = "C" + rs.getLong(1);
                        Object[] t = ticks.get(key);
                        custom.add(new Task(key, rs.getLong(1), rs.getString(2), false, t != null,
                                t == null ? null : new MemberRef((Long) t[1], (String) t[2]), t == null ? null : (Instant) t[0],
                                (rs.getBoolean(4) ? "Daily" : "Today only") + (wv ? " · visible to workers" : " · owner only"),
                                false, wv, rs.getBoolean(4), new Link("CHECKLIST", rs.getLong(1))));
                    }
                    return 0;
                }).list();
        int done = (int) (built.stream().filter(Task::done).count() + custom.stream().filter(Task::done).count());
        return new Checklist(date, done, built.size() + custom.size(), built, custom);
    }

    /** {doneAt, memberId, memberName} of the first cow record of the session, or null. */
    private Object[] milkLogged(Long farmId, LocalDate date, String session) {
        return jdbc.sql("""
                select p.created_at, m.id, m.name from production_record p
                left join farm_member m on m.farm_id = p.farm_id and m.user_id = p.created_by
                where p.farm_id = :f and p.record_date = :d and p.session = :s and p.animal_id is not null
                order by p.created_at limit 1""")
                .param("f", farmId).param("d", date).param("s", session)
                .query((rs, i) -> new Object[] {rs.getTimestamp(1).toInstant(), rs.getObject(2) == null ? null : rs.getLong(2),
                        rs.getString(3)})
                .optional().orElse(null);
    }

    private static Task task(String key, String name, Object[] auto, Map<String, Object[]> ticks, String pending,
                             boolean isAuto, Link link) {
        Object[] src = auto != null ? auto : ticks.get(key);
        boolean done = src != null;
        MemberRef by = done && (src[1] != null || src[2] != null) ? new MemberRef((Long) src[1], (String) src[2]) : null;
        Instant at = done ? (Instant) src[0] : null;
        String detail;
        if (done) {
            String t = at == null ? "" : LocalDateTime.ofInstant(at, FarmService.FARM_ZONE).toLocalTime().format(TIME);
            detail = (by == null || by.name() == null ? "" : by.name() + (t.isEmpty() ? "" : " · ")) + t;
            if (detail.isEmpty()) {
                detail = "done";
            }
        } else {
            detail = pending;
        }
        return new Task(key, null, name, true, done, by, at, detail, isAuto, true, null, link);
    }

    @Transactional
    public Checklist tick(Long farmId, String key, LocalDate date, boolean done, boolean owner) {
        LocalDate today = DailyDates.today();
        if (date.isAfter(today)) {
            throw ApiException.badRequest("You can't tick tasks for a future day.");
        }
        if (date.isBefore(today.minusDays(7))) {
            throw ApiException.badRequest("Tasks older than a week can't be changed.");
        }
        Checklist current = checklist(farmId, date, owner);
        Task t = java.util.stream.Stream.concat(current.builtIn().stream(), current.custom().stream())
                .filter(x -> x.key().equals(key)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Task not found."));
        if (!done && t.done() && t.auto() && !hasTick(farmId, key, date)) {
            throw ApiException.badRequest("This one is ticked automatically when it's recorded.");
        }
        if (done) {
            jdbc.sql("""
                    insert into checklist_tick (farm_id, task_key, tick_date, member_id) values (:f, :k, :d, :m)
                    on conflict (farm_id, task_key, tick_date) do nothing""")
                    .param("f", farmId).param("k", key).param("d", date).param("m", ctx.memberId()).update();
        } else {
            jdbc.sql("delete from checklist_tick where farm_id = :f and task_key = :k and tick_date = :d")
                    .param("f", farmId).param("k", key).param("d", date).update();
        }
        return checklist(farmId, date, owner);
    }

    private boolean hasTick(Long farmId, String key, LocalDate date) {
        return jdbc.sql("select count(*) from checklist_tick where farm_id = :f and task_key = :k and tick_date = :d")
                .param("f", farmId).param("k", key).param("d", date).query(Long.class).single() > 0;
    }

    @Transactional
    public Checklist createTask(Long farmId, TaskRequest req) {
        boolean daily = req.repeatDaily() == null || req.repeatDaily();
        LocalDate date = daily ? null : req.date() != null ? req.date() : DailyDates.today();
        jdbc.sql("""
                insert into checklist_task (farm_id, name, repeat_daily, worker_visible, task_date, sort_order)
                values (:f, :n, :r, :w, :d, (select coalesce(max(sort_order), 0) + 1 from checklist_task where farm_id = :f))""")
                .param("f", farmId).param("n", req.name().trim()).param("r", daily)
                .param("w", req.workerVisible() == null || req.workerVisible()).param("d", date).update();
        return checklist(farmId, date == null ? DailyDates.today() : date, true);
    }

    @Transactional
    public Checklist updateTask(Long farmId, Long id, TaskRequest req) {
        boolean daily = req.repeatDaily() == null || req.repeatDaily();
        int n = jdbc.sql("""
                update checklist_task set name = :n, repeat_daily = :r, worker_visible = :w,
                       task_date = case when :r then null else coalesce(:d, task_date, current_date) end, updated_at = now()
                where id = :id and farm_id = :f""")
                .param("n", req.name().trim()).param("r", daily).param("w", req.workerVisible() == null || req.workerVisible())
                .param("d", req.date()).param("id", id).param("f", farmId).update();
        if (n == 0) {
            throw ApiException.notFound("Task not found.");
        }
        return checklist(farmId, DailyDates.today(), true);
    }

    @Transactional
    public void deleteTask(Long farmId, Long id) {
        int n = jdbc.sql("delete from checklist_task where id = :id and farm_id = :f").param("id", id).param("f", farmId).update();
        if (n == 0) {
            throw ApiException.notFound("Task not found.");
        }
        jdbc.sql("delete from checklist_tick where farm_id = :f and task_key = :k").param("f", farmId).param("k", "C" + id).update();
    }

    // ---------- barn ----------

    @Transactional
    public Barn addBarn(Long farmId, BarnRequest req) {
        DailyDates.notFuture(req.date());
        BigDecimal thi = thi(req.temperatureC(), req.humidityPct());
        FarmEvent e = new FarmEvent(farmId, EventType.BARN_READING);
        e.setEventDate(req.date());
        e.setEventTime(req.time() != null ? req.time() : LocalTime.now(FarmService.FARM_ZONE).withSecond(0).withNano(0));
        e.setDetails(FarmEvent.Details.barn(req.temperatureC(), req.humidityPct(), thi));
        events.save(e);
        return barn(e);
    }

    @Transactional(readOnly = true)
    public Barn latestBarn(Long farmId) {
        return jdbc.sql("""
                select id from farm_event where farm_id = :f and event_type = 'BARN_READING'
                order by event_date desc, event_time desc nulls last, id desc limit 1""")
                .param("f", farmId).query(Long.class).optional()
                .flatMap(events::findById).map(HomeService::barn).orElse(null);
    }

    private Barn latestBarn(Long farmId, LocalDate today) {
        Barn b = latestBarn(farmId);
        return b != null && !b.date().isBefore(today.minusDays(1)) ? b : null;
    }

    private Barn barnOn(Long farmId, LocalDate date) {
        Barn b = latestBarn(farmId);
        if (b != null && b.date().equals(date)) {
            return b;
        }
        return jdbc.sql("""
                select id from farm_event where farm_id = :f and event_type = 'BARN_READING' and event_date = :d
                order by event_time desc nulls last, id desc limit 1""")
                .param("f", farmId).param("d", date).query(Long.class).optional()
                .flatMap(events::findById).map(HomeService::barn).orElse(null);
    }

    static Barn barn(FarmEvent e) {
        var d = e.getDetails();
        BigDecimal thi = d.thi() != null ? d.thi() : thi(d.temperatureC(), d.humidityPct());
        BarnLevel level = level(thi);
        return new Barn(e.getEventDate(), e.getEventTime(), d.temperatureC(), d.humidityPct(), thi, level, advice(level),
                yieldEffect(level));
    }

    /** Temperature-humidity index: (1.8T + 32) − (0.55 − 0.0055·RH)(1.8T − 26). */
    public static BigDecimal thi(BigDecimal t, BigDecimal rh) {
        double tc = t.doubleValue(), h = rh.doubleValue();
        double v = (1.8 * tc + 32) - (0.55 - 0.0055 * h) * (1.8 * tc - 26);
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }

    public static BarnLevel level(BigDecimal thi) {
        double v = thi.doubleValue();
        return v < 72 ? BarnLevel.NONE : v < 75 ? BarnLevel.MILD : v < 79 ? BarnLevel.MODERATE : v <= 88 ? BarnLevel.SEVERE
                : BarnLevel.EMERGENCY;
    }

    private static String advice(BarnLevel l) {
        return switch (l) {
            case NONE -> "No heat stress. Keep water troughs full.";
            case MILD -> "Mild stress. Keep shade and fresh water available.";
            case MODERATE -> "Run fans and sprinklers, give extra water.";
            case SEVERE -> "Severe stress. Fans, sprinklers and shade now; feed in the cooler hours.";
            case EMERGENCY -> "Emergency. Cool the animals with water and call the vet if any are panting with open mouths.";
        };
    }

    private static String yieldEffect(BarnLevel l) {
        return switch (l) {
            case NONE -> "No effect on yield expected.";
            case MILD -> "Up to 5 % lower yield.";
            case MODERATE -> "Expect 5–10 % lower evening yield.";
            case SEVERE -> "Expect 10–20 % lower yield.";
            case EMERGENCY -> "Large yield loss and risk to life.";
        };
    }

    private Farm farm(Long farmId) {
        return farms.findById(farmId).orElseThrow(() -> ApiException.notFound("Farm not found."));
    }

    private static String tagName(String tag, String name) {
        return name == null || name.isBlank() ? tag : tag + " " + name;
    }
}
