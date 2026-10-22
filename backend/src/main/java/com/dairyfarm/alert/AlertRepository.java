package com.dairyfarm.alert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.dairyfarm.alert.AlertViews.Action;
import com.dairyfarm.alert.AlertViews.ActionKind;
import com.dairyfarm.alert.AlertViews.AlertCategory;
import com.dairyfarm.alert.AlertViews.AlertStatus;
import com.dairyfarm.alert.AlertViews.Fact;
import com.dairyfarm.alert.AlertViews.Repeat;
import com.dairyfarm.alert.AlertViews.ReminderType;
import com.dairyfarm.alert.AlertViews.Severity;
import com.dairyfarm.alert.AlertViews.Source;

import tools.jackson.databind.json.JsonMapper;

/** SQL for the alert table. */
@Repository
public class AlertRepository {

    /** What is stored in alert.details. */
    public record Details(String why, List<Fact> facts, Action action, String notes) {
        public static final Details EMPTY = new Details(null, List.of(), Action.NONE, null);
    }

    /** One rule hit to upsert. */
    public record Hit(String code, String key, AlertCategory category, Severity severity, String title, String detail,
                      Long animalId, String relatedType, Long relatedId, LocalDate dueDate, LocalTime dueTime,
                      boolean workerVisible, Details details) {
    }

    public record Row(Long id, Source source, String ruleCode, String ruleKey, AlertCategory category, Severity severity,
                      String title, String detail, Long animalId, String animalTag, String animalName, String animalCategory,
                      String animalBreed, String relatedType, Long relatedId, LocalDate dueDate, LocalTime dueTime,
                      boolean workerVisible, AlertStatus status, Instant snoozedUntil, Long doneBy, String doneByName,
                      Instant doneAt, boolean autoClosed, Repeat repeat, ReminderType reminderType, Details details,
                      UUID createdBy) {
    }

    private static final String SELECT = """
            select a.*, an.tag as animal_tag, an.name as animal_name, an.category as animal_category, an.breed as animal_breed,
                   m.name as done_by_name
            from alert a left join animal an on an.id = a.animal_id left join farm_member m on m.id = a.done_by
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AlertRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Row> open(Long farmId) {
        return jdbc.sql(SELECT + " where a.farm_id = :f and a.status = 'OPEN' order by a.due_date, a.id")
                .param("f", farmId).query(this::row).list();
    }

    public List<Row> byStatus(Long farmId, AlertStatus status, int limit) {
        return jdbc.sql(SELECT + " where a.farm_id = :f and a.status = :s order by coalesce(a.done_at, a.updated_at) desc limit :l")
                .param("f", farmId).param("s", status.name()).param("l", limit).query(this::row).list();
    }

    public Optional<Row> find(Long farmId, Long id) {
        return jdbc.sql(SELECT + " where a.farm_id = :f and a.id = :id").param("f", farmId).param("id", id).query(this::row).optional();
    }

    /** Upserts the current rule hits and closes rule alerts whose condition no longer holds. Returns {created, closed}. */
    public int[] apply(Long farmId, List<Hit> hits) {
        Map<String, Object[]> existing = new HashMap<>();
        jdbc.sql("select id, rule_key, status from alert where farm_id = :f and source = 'RULE' and rule_key is not null")
                .param("f", farmId)
                .query((rs, i) -> existing.put(rs.getString("rule_key"), new Object[] {rs.getLong("id"), rs.getString("status")}))
                .list();
        Set<String> seen = new HashSet<>();
        int created = 0;
        for (Hit h : hits) {
            if (!seen.add(h.key())) {
                continue;
            }
            Object[] e = existing.get(h.key());
            if (e == null) {
                jdbc.sql("""
                        insert into alert (farm_id, source, rule_code, rule_key, category, severity, title, detail, animal_id,
                                           related_type, related_id, due_date, due_time, worker_visible, details)
                        values (:f, 'RULE', :code, :key, :cat, :sev, :title, :detail, :animal, :rt, :rid, :due, :time, :wv,
                                cast(:details as jsonb))""")
                        .param("f", farmId).param("code", h.code()).param("key", h.key()).param("cat", h.category().name())
                        .param("sev", h.severity().name()).param("title", h.title()).param("detail", h.detail())
                        .param("animal", h.animalId()).param("rt", h.relatedType()).param("rid", h.relatedId())
                        .param("due", h.dueDate()).param("time", h.dueTime()).param("wv", h.workerVisible())
                        .param("details", json.writeValueAsString(h.details())).update();
                created++;
            } else if ("OPEN".equals(e[1])) {
                jdbc.sql("""
                        update alert set category = :cat, severity = :sev, title = :title, detail = :detail, due_date = :due,
                               due_time = :time, worker_visible = :wv, details = cast(:details as jsonb), updated_at = now()
                        where id = :id""")
                        .param("cat", h.category().name()).param("sev", h.severity().name()).param("title", h.title())
                        .param("detail", h.detail()).param("due", h.dueDate()).param("time", h.dueTime())
                        .param("wv", h.workerVisible()).param("details", json.writeValueAsString(h.details()))
                        .param("id", e[0]).update();
            }
        }
        int closed = 0;
        for (Map.Entry<String, Object[]> e : existing.entrySet()) {
            if (seen.contains(e.getKey())) {
                continue;
            }
            if ("OPEN".equals(e.getValue()[1])) {
                closed += jdbc.sql("""
                        update alert set status = 'DONE', auto_closed = true, done_at = now(), rule_key = null, updated_at = now()
                        where id = :id""").param("id", e.getValue()[0]).update();
            } else {
                // Closed by a person and the condition has cleared: release the key so a later recurrence alerts again.
                jdbc.sql("update alert set rule_key = null where id = :id").param("id", e.getValue()[0]).update();
            }
        }
        return new int[] {created, closed};
    }

    public void setStatus(Long id, AlertStatus status, Long memberId) {
        jdbc.sql("update alert set status = :s, done_by = :m, done_at = now(), snoozed_until = null, updated_at = now() where id = :id")
                .param("s", status.name()).param("m", memberId).param("id", id).update();
    }

    public void snooze(Long id, Instant until) {
        jdbc.sql("update alert set snoozed_until = :u, updated_at = now() where id = :id")
                .param("u", Timestamp.from(until)).param("id", id).update();
    }

    public Long insertReminder(Long farmId, String title, Details details, LocalDate date, LocalTime time, ReminderType type,
                               Long animalId, boolean workerVisible, Repeat repeat, UUID createdBy) {
        AlertCategory cat = switch (type) {
            case HEALTH, VET -> AlertCategory.HEALTH;
            case BREED -> AlertCategory.REPRODUCTION;
            case FEED -> AlertCategory.FEED;
            case OTHER -> AlertCategory.FINANCE_OPS;
        };
        return jdbc.sql("""
                insert into alert (farm_id, source, category, severity, title, detail, animal_id, due_date, due_time,
                                   worker_visible, repeat, reminder_type, details, created_by)
                values (:f, 'REMINDER', :cat, 'MEDIUM', :title, :detail, :animal, :due, :time, :wv, :rep, :type,
                        cast(:details as jsonb), :by) returning id""")
                .param("f", farmId).param("cat", cat.name()).param("title", title).param("detail", details.notes())
                .param("animal", animalId).param("due", date).param("time", time).param("wv", workerVisible)
                .param("rep", repeat.name()).param("type", type.name()).param("details", json.writeValueAsString(details))
                .param("by", createdBy).query(Long.class).single();
    }

    public void updateReminder(Long id, String title, Details details, LocalDate date, LocalTime time, ReminderType type,
                               Long animalId, boolean workerVisible, Repeat repeat) {
        AlertCategory cat = switch (type) {
            case HEALTH, VET -> AlertCategory.HEALTH;
            case BREED -> AlertCategory.REPRODUCTION;
            case FEED -> AlertCategory.FEED;
            case OTHER -> AlertCategory.FINANCE_OPS;
        };
        jdbc.sql("""
                update alert set category = :cat, title = :title, detail = :detail, animal_id = :animal, due_date = :due,
                       due_time = :time, worker_visible = :wv, repeat = :rep, reminder_type = :type,
                       details = cast(:details as jsonb), updated_at = now()
                where id = :id""")
                .param("cat", cat.name()).param("title", title).param("detail", details.notes()).param("animal", animalId)
                .param("due", date).param("time", time).param("wv", workerVisible).param("rep", repeat.name())
                .param("type", type.name()).param("details", json.writeValueAsString(details)).param("id", id).update();
    }

    public void delete(Long id) {
        jdbc.sql("delete from alert where id = :id").param("id", id).update();
    }

    public Instant lastRefresh(Long farmId) {
        return jdbc.sql("select alerts_refreshed_at from farm where id = :f").param("f", farmId)
                .query((rs, i) -> {
                    Timestamp t = rs.getTimestamp(1);
                    return Optional.ofNullable(t == null ? null : t.toInstant());
                }).single().orElse(null);
    }

    /** Something changed on the farm: the next read of alerts or home runs the rules again. */
    public void markStale(Long farmId) {
        jdbc.sql("update farm set alerts_refreshed_at = null where id = :f and alerts_refreshed_at is not null")
                .param("f", farmId).update();
    }

    public void markRefreshed(Long farmId) {
        jdbc.sql("update farm set alerts_refreshed_at = now() where id = :f").param("f", farmId).update();
    }

    public List<Long> farmIds() {
        return jdbc.sql("select id from farm order by id").query(Long.class).list();
    }

    private Row row(ResultSet rs, int i) throws SQLException {
        Timestamp snooze = rs.getTimestamp("snoozed_until");
        Timestamp doneAt = rs.getTimestamp("done_at");
        java.sql.Time t = rs.getTime("due_time");
        long animal = rs.getLong("animal_id");
        boolean noAnimal = rs.wasNull();
        long doneBy = rs.getLong("done_by");
        boolean noDoneBy = rs.wasNull();
        long rel = rs.getLong("related_id");
        boolean noRel = rs.wasNull();
        String rt = rs.getString("reminder_type");
        String createdBy = rs.getString("created_by");
        Details d;
        try {
            d = json.readValue(rs.getString("details"), Details.class);
        } catch (RuntimeException ex) {
            d = Details.EMPTY;
        }
        if (d.action() == null) {
            d = new Details(d.why(), d.facts(), Action.NONE, d.notes());
        }
        return new Row(rs.getLong("id"), Source.valueOf(rs.getString("source")), rs.getString("rule_code"),
                rs.getString("rule_key"), AlertCategory.valueOf(rs.getString("category")),
                Severity.valueOf(rs.getString("severity")), rs.getString("title"), rs.getString("detail"),
                noAnimal ? null : animal, rs.getString("animal_tag"), rs.getString("animal_name"),
                rs.getString("animal_category"), rs.getString("animal_breed"), rs.getString("related_type"),
                noRel ? null : rel, rs.getObject("due_date", LocalDate.class), t == null ? null : t.toLocalTime(),
                rs.getBoolean("worker_visible"), AlertStatus.valueOf(rs.getString("status")),
                snooze == null ? null : snooze.toInstant(), noDoneBy ? null : doneBy, rs.getString("done_by_name"),
                doneAt == null ? null : doneAt.toInstant(), rs.getBoolean("auto_closed"), Repeat.valueOf(rs.getString("repeat")),
                rt == null ? null : ReminderType.valueOf(rt), d, createdBy == null ? null : UUID.fromString(createdBy));
    }

    static Action action(ActionKind kind, String label, Map<String, Object> params) {
        return new Action(kind, label, params);
    }
}
