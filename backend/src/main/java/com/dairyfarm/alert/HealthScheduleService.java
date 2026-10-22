package com.dairyfarm.alert;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.alert.AlertViews.AppliesTo;
import com.dairyfarm.alert.AlertViews.Schedule;
import com.dairyfarm.alert.AlertViews.ScheduleRequest;
import com.dairyfarm.alert.AlertViews.ScheduleType;
import com.dairyfarm.alert.AlertViews.Step;
import com.dairyfarm.common.ApiException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Deworming and vaccination schedules (defaults seeded once per farm) and the doses due from them. */
@Service
public class HealthScheduleService {

    /** One dose that is due (or coming up) for one animal. */
    public record Due(Schedule schedule, Long animalId, String tag, String name, String stepLabel, String stepKey,
                      LocalDate date) {
    }

    private record Given(LocalDate date, String type, Long scheduleId, String step, String text) {
    }

    private static final TypeReference<List<Step>> STEPS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public HealthScheduleService(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    static List<ScheduleRequest> defaults() {
        return List.of(
                new ScheduleRequest(ScheduleType.DEWORMING, "Calf & heifer deworming", AppliesTo.ALL, null, List.of(
                        new Step("Day 15", 15, null, null),
                        new Step("Day 30", 30, null, null),
                        new Step("Every 2 months until 6 months", 60, 60, 180),
                        new Step("Every 3 months until 1 year", 270, 90, 360),
                        new Step("Yearly after 1 year", 725, 365, null)), 2, true, true),
                new ScheduleRequest(ScheduleType.VACCINATION, "FMD vaccination", AppliesTo.ALL, "FMD vaccine", List.of(
                        new Step("At 4 months of age", 120, null, null),
                        new Step("Booster", 141, null, null),
                        new Step("Every 6 months", 321, 180, null)), 2, true, true));
    }

    /** Adds the default schedules the first time a farm uses them. */
    @Transactional
    public void seedIfNeeded(Long farmId) {
        Boolean seeded = jdbc.sql("select schedules_seeded from farm where id = :f").param("f", farmId).query(Boolean.class).single();
        if (Boolean.TRUE.equals(seeded)) {
            return;
        }
        int done = jdbc.sql("update farm set schedules_seeded = true where id = :f and not schedules_seeded").param("f", farmId).update();
        if (done == 0) {
            return;
        }
        defaults().forEach(r -> insert(farmId, r));
    }

    @Transactional
    public List<Schedule> list(Long farmId) {
        seedIfNeeded(farmId);
        return jdbc.sql("select * from health_schedule where farm_id = :f order by type, id").param("f", farmId)
                .query((rs, i) -> new Schedule(rs.getLong("id"), ScheduleType.valueOf(rs.getString("type")), rs.getString("name"),
                        AppliesTo.valueOf(rs.getString("applies_to")), rs.getString("medicine"),
                        json.readValue(rs.getString("steps"), STEPS), rs.getInt("alert_days_before"),
                        rs.getBoolean("worker_visible"), rs.getBoolean("active")))
                .list();
    }

    @Transactional
    public Schedule get(Long farmId, Long id) {
        return list(farmId).stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Schedule not found."));
    }

    @Transactional
    public Schedule create(Long farmId, ScheduleRequest req) {
        seedIfNeeded(farmId);
        return get(farmId, insert(farmId, req));
    }

    @Transactional
    public Schedule update(Long farmId, Long id, ScheduleRequest req) {
        get(farmId, id);
        check(req);
        jdbc.sql("""
                update health_schedule set type = :t, name = :n, applies_to = :a, medicine = :m, steps = cast(:s as jsonb),
                       alert_days_before = :d, worker_visible = :wv, active = :act, updated_at = now()
                where id = :id and farm_id = :f""")
                .param("t", req.type().name()).param("n", req.name().trim()).param("a", req.appliesTo().name())
                .param("m", blank(req.medicine())).param("s", json.writeValueAsString(req.steps()))
                .param("d", req.alertDaysBefore() == null ? 2 : req.alertDaysBefore())
                .param("wv", req.workerVisible() == null || req.workerVisible())
                .param("act", req.active() == null || req.active()).param("id", id).param("f", farmId).update();
        return get(farmId, id);
    }

    @Transactional
    public void delete(Long farmId, Long id) {
        get(farmId, id);
        jdbc.sql("delete from health_schedule where id = :id and farm_id = :f").param("id", id).param("f", farmId).update();
    }

    private Long insert(Long farmId, ScheduleRequest req) {
        check(req);
        return jdbc.sql("""
                insert into health_schedule (farm_id, type, name, applies_to, medicine, steps, alert_days_before, worker_visible, active)
                values (:f, :t, :n, :a, :m, cast(:s as jsonb), :d, :wv, :act) returning id""")
                .param("f", farmId).param("t", req.type().name()).param("n", req.name().trim()).param("a", req.appliesTo().name())
                .param("m", blank(req.medicine())).param("s", json.writeValueAsString(req.steps()))
                .param("d", req.alertDaysBefore() == null ? 2 : req.alertDaysBefore())
                .param("wv", req.workerVisible() == null || req.workerVisible())
                .param("act", req.active() == null || req.active()).query(Long.class).single();
    }

    private static void check(ScheduleRequest req) {
        for (Step s : req.steps()) {
            if (s.untilAgeDays() != null && s.untilAgeDays() < s.atAgeDays()) {
                throw ApiException.badRequest(s.label() + ": the end age must be after the first dose.");
            }
            if (s.untilAgeDays() != null && s.repeatEveryDays() == null) {
                throw ApiException.badRequest(s.label() + ": set how often it repeats, or remove the end age.");
            }
        }
    }

    /** Doses due between today − 30 days and today + each schedule's alert days that have no matching health record. */
    @Transactional
    public List<Due> due(Long farmId, LocalDate today) {
        List<Schedule> schedules = list(farmId).stream().filter(Schedule::active).toList();
        if (schedules.isEmpty()) {
            return List.of();
        }
        record A(Long id, String tag, String name, String category, LocalDate dob) {
        }
        List<A> animals = jdbc.sql("""
                select id, tag, name, category, date_of_birth from animal
                where farm_id = :f and status = 'ACTIVE' and date_of_birth is not null order by tag""")
                .param("f", farmId)
                .query((rs, i) -> new A(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, LocalDate.class))).list();
        Map<Long, List<Given>> given = new HashMap<>();
        jdbc.sql("""
                select animal_id, event_date, sub_type, details->>'scheduleId', details->>'scheduleStep',
                       lower(coalesce(details->>'medicine', '') || ' ' || coalesce(details->>'diagnosis', '') || ' ' || coalesce(notes, ''))
                from farm_event where farm_id = :f and event_type = 'HEALTH' and sub_type in ('DEWORMING', 'VACCINATION')""")
                .param("f", farmId)
                .query((rs, i) -> given.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new Given(
                        rs.getObject(2, LocalDate.class), rs.getString(3),
                        rs.getString(4) == null ? null : Long.valueOf(rs.getString(4)), rs.getString(5), rs.getString(6))))
                .list();
        List<Due> out = new ArrayList<>();
        for (Schedule s : schedules) {
            LocalDate from = today.minusDays(30);
            LocalDate until = today.plusDays(s.alertDaysBefore());
            for (A a : animals) {
                if (!applies(s.appliesTo(), a.category())) {
                    continue;
                }
                List<Given> mine = given.getOrDefault(a.id(), List.of());
                Due first = null;
                for (int si = 0; si < s.steps().size() && first == null; si++) {
                    Step st = s.steps().get(si);
                    int n = 0;
                    for (int age = st.atAgeDays(); ; age += st.repeatEveryDays(), n++) {
                        if (st.untilAgeDays() != null && age > st.untilAgeDays()) {
                            break;
                        }
                        LocalDate date = a.dob().plusDays(age);
                        if (date.isAfter(until)) {
                            break;
                        }
                        String key = si + "-" + n;
                        if (!date.isBefore(from) && !done(s, key, date, mine)) {
                            first = new Due(s, a.id(), a.tag(), a.name(), st.label(), key, date);
                            break;
                        }
                        if (st.repeatEveryDays() == null) {
                            break;
                        }
                    }
                }
                if (first != null) {
                    out.add(first);
                }
            }
        }
        return out;
    }

    private static boolean applies(AppliesTo to, String category) {
        return switch (to) {
            case ALL -> true;
            case CALVES_HEIFERS -> "CALF".equals(category) || "HEIFER".equals(category);
            case MILKING -> "MILKING".equals(category);
        };
    }

    /** Given for this dose: linked to it, or a dose of the same kind from two weeks before onwards. */
    private static boolean done(Schedule s, String key, LocalDate date, List<Given> given) {
        for (Given g : given) {
            if (!s.type().name().equals(g.type())) {
                continue;
            }
            if (g.scheduleId() != null) {
                if (g.scheduleId().equals(s.id()) && key.equals(g.step())) {
                    return true;
                }
                continue;
            }
            boolean sameKind = s.type() == ScheduleType.DEWORMING
                    || g.text().contains(word(s.medicine() != null ? s.medicine() : s.name()));
            if (sameKind && !g.date().isBefore(date.minusDays(14))) {
                return true;
            }
        }
        return false;
    }

    /** First word of a vaccine name, e.g. "FMD" of "FMD vaccine". */
    private static String word(String name) {
        return name.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
