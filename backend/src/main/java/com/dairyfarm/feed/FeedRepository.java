package com.dairyfarm.feed;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.dairyfarm.feed.FeedViews.RationCategory;
import com.dairyfarm.feed.FeedViews.Targets;

import tools.jackson.databind.json.JsonMapper;

/** SQL for feed: stock moves with feed columns, feeding rows, formulas, ration rules and the animals to feed. */
@Repository
public class FeedRepository {

    public record Txn(Long id, Long itemId, LocalDate date, LocalTime time, String type, BigDecimal quantity,
                      BigDecimal cost, Long supplierId, String paymentMethod, Boolean paid, String session,
                      Long animalId, Long groupId, String sourceType, String notes, String targetLabel) {
    }

    public record NewTxn(Long itemId, String type, LocalDate date, LocalTime time, BigDecimal quantity, BigDecimal cost,
                         Long supplierId, String paymentMethod, Boolean paid, String session, Long animalId,
                         Long groupId, String sourceType, Long sourceId, UUID batchId, String notes) {
    }

    public record LastPrice(BigDecimal price, LocalDate date) {
    }

    public record SessionRow(String session, BigDecimal kg, LocalTime time, String by) {
    }

    public record AnimalRow(Long id, String tag, String name, String category, String breed, Long groupId,
                            LocalDate lastCalvingDate, BigDecimal currentWeightKg, BigDecimal measuredKg,
                            LocalDate weighedOn, BigDecimal milkYesterday) {
    }

    public record FormulaRow(Long id, String name, Long mixItemId, BigDecimal batchKg, Targets targets, String notes,
                             boolean active) {
    }

    public record LineRow(Long formulaId, Long itemId, BigDecimal kg) {
    }

    public record RuleRow(RationCategory category, LocalDate effectiveFrom, RationParams params) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public FeedRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // ---- stock moves -------------------------------------------------------------------------------------------

    public Long insert(Long farmId, NewTxn t) {
        return jdbc.sql("""
                insert into item_txn (farm_id, item_id, txn_type, txn_date, txn_time, quantity, cost, supplier_contact_id,
                                      payment_method, paid, session, animal_id, group_id, source_type, source_id, batch_id, notes, created_by)
                values (:farm, :item, :type, :date, :time, :qty, :cost, :sup, :pm, :paid, :ses, :animal, :grp, :st, :sid, :batch, :notes, :by)
                returning id""")
                .param("farm", farmId).param("item", t.itemId()).param("type", t.type()).param("date", t.date())
                .param("time", t.time()).param("qty", t.quantity()).param("cost", t.cost()).param("sup", t.supplierId())
                .param("pm", t.paymentMethod()).param("paid", t.paid()).param("ses", t.session())
                .param("animal", t.animalId()).param("grp", t.groupId()).param("st", t.sourceType())
                .param("sid", t.sourceId()).param("batch", t.batchId()).param("notes", t.notes())
                .param("by", com.dairyfarm.security.CurrentUser.idOrNull())
                .query(Long.class).single();
    }

    private static final String TXN_COLS = """
            t.id, t.item_id, t.txn_date, t.txn_time, t.txn_type, t.quantity, t.cost, t.supplier_contact_id, t.payment_method,
            t.paid, t.session, t.animal_id, t.group_id, t.source_type, t.notes,
            coalesce(a.tag, g.name) as target_label
            from item_txn t left join animal a on a.id = t.animal_id left join animal_group g on g.id = t.group_id""";

    private static Txn txn(ResultSet rs, int n) throws SQLException {
        return new Txn(rs.getLong("id"), rs.getLong("item_id"), rs.getObject("txn_date", LocalDate.class),
                rs.getObject("txn_time", LocalTime.class), rs.getString("txn_type"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("cost"), (Long) rs.getObject("supplier_contact_id"), rs.getString("payment_method"),
                (Boolean) rs.getObject("paid"), rs.getString("session"), (Long) rs.getObject("animal_id"),
                (Long) rs.getObject("group_id"), rs.getString("source_type"), rs.getString("notes"), rs.getString("target_label"));
    }

    public Optional<Txn> txn(Long farmId, Long id) {
        return jdbc.sql("select " + TXN_COLS + "\n where t.farm_id = :farm and t.id = :id")
                .param("farm", farmId).param("id", id).query(FeedRepository::txn).optional();
    }

    public List<Txn> moves(Long farmId, Long itemId, String type, LocalDate from, LocalDate to) {
        return jdbc.sql("select " + TXN_COLS + "\n" + """
                         join item i on i.id = t.item_id
                         where t.farm_id = :farm and i.item_type = 'FEED'
                           and (cast(:item as bigint) is null or t.item_id = :item)
                           and (cast(:type as varchar) is null or t.txn_type = :type)
                           and t.txn_date between :from and :to
                         order by t.txn_date desc, t.id desc limit 500""")
                .param("farm", farmId).param("item", itemId).param("type", type).param("from", from).param("to", to)
                .query(FeedRepository::txn).list();
    }

    public Map<Long, LastPrice> lastPrices(Long farmId) {
        Map<Long, LastPrice> out = new HashMap<>();
        jdbc.sql("""
                select distinct on (item_id) item_id, cost / nullif(quantity, 0) as price, txn_date from item_txn
                where farm_id = :farm and txn_type = 'PURCHASE' and cost is not null
                order by item_id, txn_date desc, id desc""")
                .param("farm", farmId).query((rs, n) -> {
                    BigDecimal p = rs.getBigDecimal("price");
                    out.put(rs.getLong("item_id"), new LastPrice(p == null ? null : p.setScale(2, java.math.RoundingMode.HALF_UP),
                            rs.getObject("txn_date", LocalDate.class)));
                    return null;
                }).list();
        return out;
    }

    public boolean hasMoves(Long itemId) {
        return jdbc.sql("select exists (select 1 from item_txn where item_id = :id)").param("id", itemId)
                .query(Boolean.class).single();
    }

    public boolean usedInFormulaOrRule(Long farmId, Long itemId) {
        return jdbc.sql("""
                select exists (select 1 from feed_formula_line l join feed_formula f on f.id = l.formula_id
                               where f.farm_id = :farm and l.item_id = :id)
                    or exists (select 1 from feed_formula where farm_id = :farm and mix_item_id = :id)
                    or exists (select 1 from ration_rule where farm_id = :farm
                               and jsonb_path_exists(params, '$.** ? (@.itemId == $id || @.concItemId == $id)',
                                                     jsonb_build_object('id', cast(:id as bigint))))""")
                .param("farm", farmId).param("id", itemId).query(Boolean.class).single();
    }

    // ---- feeding -----------------------------------------------------------------------------------------------

    /** Feeding rows (kg by item) saved for a session and target. */
    public Map<Long, BigDecimal> feeding(Long farmId, LocalDate date, String session, Long groupId, Long animalId) {
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        jdbc.sql("""
                select item_id, sum(quantity) as kg from item_txn
                where farm_id = :farm and source_type = 'FEEDING' and txn_date = :date and session = :ses
                  and group_id is not distinct from cast(:grp as bigint) and animal_id is not distinct from cast(:animal as bigint)
                group by item_id""")
                .param("farm", farmId).param("date", date).param("ses", session).param("grp", groupId).param("animal", animalId)
                .query((rs, n) -> {
                    out.put(rs.getLong("item_id"), rs.getBigDecimal("kg"));
                    return null;
                }).list();
        return out;
    }

    public int deleteFeeding(Long farmId, LocalDate date, String session, Long groupId, Long animalId) {
        return jdbc.sql("""
                delete from item_txn where farm_id = :farm and source_type = 'FEEDING' and txn_date = :date and session = :ses
                  and group_id is not distinct from cast(:grp as bigint) and animal_id is not distinct from cast(:animal as bigint)""")
                .param("farm", farmId).param("date", date).param("ses", session).param("grp", groupId).param("animal", animalId)
                .update();
    }

    /** Everything fed in each session of a day, with the last save time and who saved it. */
    public Map<String, SessionRow> sessions(Long farmId, LocalDate date) {
        Map<String, SessionRow> out = new HashMap<>();
        jdbc.sql("""
                select t.session, sum(t.quantity) as kg, max(t.txn_time) as time,
                       (select coalesce(m.name, u.full_name) from item_txn t2
                          left join app_user u on u.id = t2.created_by
                          left join farm_member m on m.farm_id = t2.farm_id and m.user_id = t2.created_by
                        where t2.farm_id = :farm and t2.source_type = 'FEEDING' and t2.txn_date = :date and t2.session = t.session
                        order by t2.id desc limit 1) as by_name
                from item_txn t
                where t.farm_id = :farm and t.source_type = 'FEEDING' and t.txn_date = :date
                group by t.session""")
                .param("farm", farmId).param("date", date).query((rs, n) -> {
                    out.put(rs.getString("session"), new SessionRow(rs.getString("session"), rs.getBigDecimal("kg"),
                            rs.getObject("time", LocalTime.class), rs.getString("by_name")));
                    return null;
                }).list();
        return out;
    }

    // ---- animals -----------------------------------------------------------------------------------------------

    /** Active dairy animals (cows and buffalo) with what the ration needs, optionally one group or one animal. */
    public List<AnimalRow> animals(Long farmId, LocalDate date, Long groupId, Long animalId) {
        return jdbc.sql("""
                select a.id, a.tag, a.name, a.category, a.breed, a.group_id, a.last_calving_date, a.current_weight_kg,
                       w.weight_kg, w.event_date,
                       (select sum(p.quantity) from production_record p
                         where p.animal_id = a.id and p.record_date = cast(:date as date) - 1) as milk
                from animal a
                left join lateral (select e.weight_kg, e.event_date from farm_event e
                                   where e.animal_id = a.id and e.event_type = 'MEASUREMENT' and e.weight_kg is not null
                                     and e.event_date <= :date order by e.event_date desc, e.id desc limit 1) w on true
                where a.farm_id = :farm and a.status = 'ACTIVE' and a.species in ('COW', 'BUFFALO')
                  and a.category in ('CALF', 'HEIFER', 'MILKING', 'DRY', 'BULL')
                  and (cast(:grp as bigint) is null or a.group_id = :grp)
                  and (cast(:animal as bigint) is null or a.id = :animal)
                order by a.tag""")
                .param("farm", farmId).param("date", date).param("grp", groupId).param("animal", animalId)
                .query((rs, n) -> new AnimalRow(rs.getLong("id"), rs.getString("tag"), rs.getString("name"),
                        rs.getString("category"), rs.getString("breed"), (Long) rs.getObject("group_id"),
                        rs.getObject("last_calving_date", LocalDate.class), rs.getBigDecimal("current_weight_kg"),
                        rs.getBigDecimal("weight_kg"), rs.getObject("event_date", LocalDate.class), rs.getBigDecimal("milk")))
                .list();
    }

    public Optional<String> groupName(Long farmId, Long groupId) {
        return jdbc.sql("select name from animal_group where farm_id = :farm and id = :id")
                .param("farm", farmId).param("id", groupId).query(String.class).optional();
    }

    public Optional<String> memberName(Long farmId, Long memberId) {
        return jdbc.sql("select name from farm_member where farm_id = :farm and id = :id")
                .param("farm", farmId).param("id", memberId).query(String.class).optional();
    }

    // ---- formulas ----------------------------------------------------------------------------------------------

    private FormulaRow formula(ResultSet rs, int n) throws SQLException {
        String t = rs.getString("targets");
        return new FormulaRow(rs.getLong("id"), rs.getString("name"), rs.getLong("mix_item_id"), rs.getBigDecimal("batch_kg"),
                t == null ? Targets.EMPTY : json.readValue(t, Targets.class), rs.getString("notes"), rs.getBoolean("active"));
    }

    public List<FormulaRow> formulas(Long farmId) {
        return jdbc.sql("select * from feed_formula where farm_id = :farm order by active desc, lower(name)")
                .param("farm", farmId).query(this::formula).list();
    }

    public Optional<FormulaRow> formula(Long farmId, Long id) {
        return jdbc.sql("select * from feed_formula where farm_id = :farm and id = :id")
                .param("farm", farmId).param("id", id).query(this::formula).optional();
    }

    public List<LineRow> lines(Collection<Long> formulaIds) {
        if (formulaIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select formula_id, item_id, kg from feed_formula_line where formula_id in (:ids) order by sort_order, id")
                .param("ids", formulaIds)
                .query((rs, n) -> new LineRow(rs.getLong("formula_id"), rs.getLong("item_id"), rs.getBigDecimal("kg"))).list();
    }

    public boolean formulaNameTaken(Long farmId, String name, Long exceptId) {
        return jdbc.sql("select exists (select 1 from feed_formula where farm_id = :farm and lower(name) = lower(:name) "
                        + "and id <> coalesce(cast(:id as bigint), -1))")
                .param("farm", farmId).param("name", name).param("id", exceptId).query(Boolean.class).single();
    }

    public Long saveFormula(Long farmId, Long id, String name, Long mixItemId, BigDecimal batchKg, Targets targets,
                            String notes, boolean active, List<LineRow> lines) {
        String t = json.writeValueAsString(targets == null ? Targets.EMPTY : targets);
        Long fid;
        if (id == null) {
            fid = jdbc.sql("""
                    insert into feed_formula (farm_id, name, mix_item_id, batch_kg, targets, notes, active)
                    values (:farm, :name, :mix, :batch, cast(:t as jsonb), :notes, :active) returning id""")
                    .param("farm", farmId).param("name", name).param("mix", mixItemId).param("batch", batchKg)
                    .param("t", t).param("notes", notes).param("active", active).query(Long.class).single();
        } else {
            jdbc.sql("""
                    update feed_formula set name = :name, mix_item_id = :mix, batch_kg = :batch, targets = cast(:t as jsonb),
                           notes = :notes, active = :active, updated_at = now() where farm_id = :farm and id = :id""")
                    .param("farm", farmId).param("id", id).param("name", name).param("mix", mixItemId).param("batch", batchKg)
                    .param("t", t).param("notes", notes).param("active", active).update();
            jdbc.sql("delete from feed_formula_line where formula_id = :id").param("id", id).update();
            fid = id;
        }
        int order = 0;
        for (LineRow l : lines) {
            jdbc.sql("insert into feed_formula_line (formula_id, item_id, kg, sort_order) values (:f, :i, :kg, :o)")
                    .param("f", fid).param("i", l.itemId()).param("kg", l.kg()).param("o", order++).update();
        }
        return fid;
    }

    public int deleteFormula(Long farmId, Long id) {
        return jdbc.sql("delete from feed_formula where farm_id = :farm and id = :id").param("farm", farmId).param("id", id)
                .update();
    }

    // ---- ration rules ------------------------------------------------------------------------------------------

    public List<RuleRow> rules(Long farmId) {
        return jdbc.sql("select category, effective_from, params from ration_rule where farm_id = :farm "
                        + "order by category, effective_from")
                .param("farm", farmId)
                .query((rs, n) -> new RuleRow(RationCategory.valueOf(rs.getString("category")),
                        rs.getObject("effective_from", LocalDate.class), json.readValue(rs.getString("params"), RationParams.class)))
                .list();
    }

    public void upsertRule(Long farmId, RationCategory category, LocalDate from, RationParams params) {
        jdbc.sql("""
                insert into ration_rule (farm_id, category, effective_from, params) values (:farm, :cat, :from, cast(:p as jsonb))
                on conflict (farm_id, category, effective_from) do update set params = excluded.params, updated_at = now()""")
                .param("farm", farmId).param("cat", category.name()).param("from", from)
                .param("p", json.writeValueAsString(params)).update();
    }

    /** The rule in force for each category on a date (latest effective_from ≤ date). */
    public static Map<RationCategory, RuleRow> inForce(List<RuleRow> rules, LocalDate date) {
        Map<RationCategory, RuleRow> out = new HashMap<>();
        for (RuleRow r : rules) {
            if (!r.effectiveFrom().isAfter(date)) {
                RuleRow cur = out.get(r.category());
                if (cur == null || r.effectiveFrom().isAfter(cur.effectiveFrom())) {
                    out.put(r.category(), r);
                }
            }
        }
        return out;
    }

    static <T> List<T> list(Collection<T> c) {
        return new ArrayList<>(c);
    }
}
