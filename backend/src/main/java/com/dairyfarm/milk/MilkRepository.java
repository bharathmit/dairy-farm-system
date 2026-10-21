package com.dairyfarm.milk;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.dairyfarm.milk.MilkViews.Product;
import com.dairyfarm.milk.MilkViews.Session;
import com.dairyfarm.milk.MilkViews.TrendPoint;

/** SQL for production_record (cow sessions and farm totals/sales). */
@Repository
public class MilkRepository {

    public record CowInfo(Long id, String tag, String name, String breed, Long groupId, LocalDate lastCalvingDate,
                          LocalDate dateOfBirth, String sex, String category, String status) {
        public boolean milking() {
            return "ACTIVE".equals(status) && "FEMALE".equals(sex) && "MILKING".equals(category);
        }
    }

    public record CowRow(Long animalId, LocalDate date, Session session, BigDecimal quantity, BigDecimal fatPct,
                         BigDecimal snfPct, BigDecimal proteinPct, BigDecimal density, Integer scc, boolean withheld,
                         String notes) {
    }

    public record SaleRow(Long id, LocalDate date, Session session, Product product, BigDecimal quantity,
                          BigDecimal soldQty, BigDecimal rate, BigDecimal incomeAmount, Long buyerContactId,
                          BigDecimal fatPct, BigDecimal snfPct, Boolean paid, String notes) {
    }

    public record Totals(BigDecimal litres, BigDecimal withheldLitres, int cows) {
    }

    private static final String COW_COLS =
            "id, tag, name, breed, group_id, last_calving_date, date_of_birth, sex, category, status";
    private static final String ROW_COLS =
            "animal_id, record_date, session, quantity, fat_pct, snf_pct, protein_pct, density, scc, withheld, notes";
    private static final String SALE_COLS = "id, record_date, session, product, quantity, sold_qty, rate, "
            + "income_amount, buyer_contact_id, fat_pct, snf_pct, paid, notes";

    private final JdbcClient jdbc;

    public MilkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- animals

    public List<CowInfo> milkingCows(Long farmId, Long groupId) {
        String sql = "select " + COW_COLS + " from animal where farm_id = :f and status = 'ACTIVE' and sex = 'FEMALE'"
                + " and category = 'MILKING'" + (groupId == null ? "" : " and group_id = :g") + " order by lower(tag)";
        var spec = jdbc.sql(sql).param("f", farmId);
        if (groupId != null) {
            spec = spec.param("g", groupId);
        }
        return spec.query(MilkRepository::cow).list();
    }

    public List<CowInfo> animals(Long farmId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select " + COW_COLS + " from animal where farm_id = :f and id in (:ids)")
                .param("f", farmId).param("ids", ids).query(MilkRepository::cow).list();
    }

    public Optional<CowInfo> animal(Long farmId, Long id) {
        return animals(farmId, List.of(id)).stream().findFirst();
    }

    /** Best AM+PM day in the 365 days before each cow's last calving. */
    public Map<Long, BigDecimal> ownPeaks(Long farmId, Collection<Long> ids) {
        Map<Long, BigDecimal> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.sql("""
                select a.id, max(d.total) as peak
                from animal a
                join (select animal_id, record_date, sum(quantity) as total
                      from production_record
                      where farm_id = :f and animal_id in (:ids)
                      group by animal_id, record_date having count(*) = 2) d on d.animal_id = a.id
                where a.farm_id = :f and a.last_calving_date is not null
                  and d.record_date >= a.last_calving_date - 365 and d.record_date < a.last_calving_date
                group by a.id""")
                .param("f", farmId).param("ids", ids)
                .query(rs -> {
                    out.put(rs.getLong("id"), rs.getBigDecimal("peak"));
                });
        return out;
    }

    // ---- cow sessions

    public List<CowRow> cowRows(Long farmId, LocalDate from, LocalDate to, Long animalId) {
        String sql = "select " + ROW_COLS + " from production_record where farm_id = :f and animal_id is not null"
                + " and record_date between :from and :to" + (animalId == null ? "" : " and animal_id = :a")
                + " order by record_date, animal_id, session";
        var spec = jdbc.sql(sql).param("f", farmId).param("from", from).param("to", to);
        if (animalId != null) {
            spec = spec.param("a", animalId);
        }
        return spec.query(MilkRepository::row).list();
    }

    /** Up to 7 latest litres per cow for one session before a date, newest first. */
    public Map<Long, List<BigDecimal>> recent(Long farmId, Collection<Long> ids, Session session, LocalDate before) {
        Map<Long, List<BigDecimal>> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.sql("""
                select animal_id, quantity from (
                  select animal_id, quantity, record_date,
                         row_number() over (partition by animal_id order by record_date desc) as rn
                  from production_record
                  where farm_id = :f and animal_id in (:ids) and session = :s and record_date < :d
                    and record_date >= cast(:d as date) - 120 and quantity is not null) x
                where rn <= 7 order by animal_id, record_date desc""")
                .param("f", farmId).param("ids", ids).param("s", session.name()).param("d", before)
                .query(rs -> {
                    out.computeIfAbsent(rs.getLong("animal_id"), k -> new ArrayList<>()).add(rs.getBigDecimal("quantity"));
                });
        return out;
    }

    /** Saves litres (sheet) or litres plus quality and notes (single-cow form). */
    public void upsertCowSession(Long farmId, Long animalId, LocalDate date, Session session, BigDecimal litres,
                                 boolean withQuality, BigDecimal fat, BigDecimal snf, BigDecimal protein,
                                 BigDecimal density, Integer scc, String notes, UUID userId) {
        String update = withQuality
                ? "quantity = excluded.quantity, fat_pct = excluded.fat_pct, snf_pct = excluded.snf_pct, "
                  + "protein_pct = excluded.protein_pct, density = excluded.density, scc = excluded.scc, notes = excluded.notes"
                : "quantity = excluded.quantity";
        jdbc.sql("""
                insert into production_record (farm_id, record_date, session, product, animal_id, quantity, unit,
                    fat_pct, snf_pct, protein_pct, density, scc, notes, created_by)
                values (:f, :d, :s, 'MILK', :a, :q, 'L', :fat, :snf, :protein, :density, :scc, :notes, :u)
                on conflict (animal_id, record_date, session) where animal_id is not null
                do update set\s""" + update + ", updated_at = now()")
                .param("f", farmId).param("d", date).param("s", session.name()).param("a", animalId)
                .param("q", litres).param("fat", fat).param("snf", snf).param("protein", protein)
                .param("density", density).param("scc", scc).param("notes", notes).param("u", userId)
                .update();
    }

    public void deleteCowSession(Long farmId, Long animalId, LocalDate date, Session session) {
        jdbc.sql("delete from production_record where farm_id = :f and animal_id = :a and record_date = :d and session = :s")
                .param("f", farmId).param("a", animalId).param("d", date).param("s", session.name()).update();
    }

    /**
     * Re-marks cow records as withheld when they were milked before an active milk withdrawal ended
     * (session time = farm milking time, Asia/Kolkata). Runs after milk or health records change.
     */
    public void refreshWithheld(Long farmId, Collection<Long> animalIds, LocalDate from, LocalDate to,
                                LocalTime morning, LocalTime evening) {
        if (animalIds.isEmpty()) {
            return;
        }
        jdbc.sql("""
                update production_record p set withheld = exists (
                    select 1 from farm_event e
                    where e.farm_id = p.farm_id and e.animal_id = p.animal_id and e.event_type = 'HEALTH'
                      and e.milk_withdrawal_until is not null and e.event_date <= p.record_date
                      and e.milk_withdrawal_until > ((p.record_date + case when p.session = 'AM'
                            then cast(:am as time) else cast(:pm as time) end) at time zone 'Asia/Kolkata'))
                where p.farm_id = :f and p.animal_id in (:ids) and p.record_date between :from and :to""")
                .param("f", farmId).param("ids", animalIds).param("from", from).param("to", to)
                .param("am", morning).param("pm", evening)
                .update();
    }

    /** Latest milk-withdrawal end per cow among withdrawals started on/before the date and still running at the time. */
    public Map<Long, Instant> withdrawals(Long farmId, Collection<Long> ids, LocalDate date, Instant at) {
        Map<Long, Instant> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.sql("""
                select animal_id, max(milk_withdrawal_until) as until from farm_event
                where farm_id = :f and event_type = 'HEALTH' and animal_id in (:ids)
                  and event_date <= :d and milk_withdrawal_until > :t
                group by animal_id""")
                .param("f", farmId).param("ids", ids).param("d", date).param("t", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                .query(rs -> {
                    out.put(rs.getLong("animal_id"), rs.getObject("until", OffsetDateTime.class).toInstant());
                });
        return out;
    }

    /** Cow-logged litres for a session (DAY = both). */
    public Totals cowTotals(Long farmId, LocalDate date, Session session) {
        String filter = session == Session.DAY ? "" : " and session = :s";
        var spec = jdbc.sql("""
                select coalesce(sum(quantity), 0) as litres,
                       coalesce(sum(quantity) filter (where withheld), 0) as withheld,
                       count(distinct animal_id) as cows
                from production_record where farm_id = :f and animal_id is not null and record_date = :d""" + filter)
                .param("f", farmId).param("d", date);
        if (session != Session.DAY) {
            spec = spec.param("s", session.name());
        }
        return spec.query((rs, i) -> new Totals(rs.getBigDecimal("litres"), rs.getBigDecimal("withheld"), rs.getInt("cows")))
                .single();
    }

    /** Cow-logged litres per day, zero-filled. */
    public List<TrendPoint> trend(Long farmId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                select cast(d as date) as day,
                       coalesce(sum(p.quantity) filter (where p.session = 'AM'), 0) as am,
                       coalesce(sum(p.quantity) filter (where p.session = 'PM'), 0) as pm,
                       coalesce(sum(p.quantity), 0) as total,
                       coalesce(sum(p.quantity) filter (where p.withheld), 0) as withheld
                from generate_series(cast(:from as date), cast(:to as date), interval '1 day') d
                left join production_record p
                  on p.farm_id = :f and p.animal_id is not null and p.record_date = cast(d as date)
                group by d order by d""")
                .param("f", farmId).param("from", from).param("to", to)
                .query((rs, i) -> new TrendPoint(rs.getObject("day", LocalDate.class), rs.getBigDecimal("am"),
                        rs.getBigDecimal("pm"), rs.getBigDecimal("total"), rs.getBigDecimal("withheld")))
                .list();
    }

    // ---- farm totals / sales

    public List<SaleRow> sales(Long farmId, LocalDate from, LocalDate to) {
        return jdbc.sql("select " + SALE_COLS + " from production_record where farm_id = :f and animal_id is null"
                        + " and record_date between :from and :to order by record_date desc, session desc, id desc")
                .param("f", farmId).param("from", from).param("to", to)
                .query(MilkRepository::sale).list();
    }

    public Optional<SaleRow> sale(Long farmId, Long id) {
        return jdbc.sql("select " + SALE_COLS + " from production_record where farm_id = :f and animal_id is null and id = :id")
                .param("f", farmId).param("id", id).query(MilkRepository::sale).optional();
    }

    public Optional<SaleRow> sale(Long farmId, LocalDate date, Session session, Product product) {
        return jdbc.sql("select " + SALE_COLS + " from production_record where farm_id = :f and animal_id is null"
                        + " and record_date = :d and session = :s and product = :p")
                .param("f", farmId).param("d", date).param("s", session.name()).param("p", product.name())
                .query(MilkRepository::sale).optional();
    }

    public Long upsertSale(Long farmId, LocalDate date, Session session, Product product, BigDecimal quantity,
                           BigDecimal soldQty, BigDecimal rate, BigDecimal income, Long buyerId, BigDecimal fat,
                           BigDecimal snf, Boolean paid, String notes, UUID userId) {
        return jdbc.sql("""
                insert into production_record (farm_id, record_date, session, product, animal_id, quantity, unit,
                    sold_qty, rate, income_amount, buyer_contact_id, fat_pct, snf_pct, paid, notes, created_by)
                values (:f, :d, :s, :p, null, :q, :unit, :sold, :rate, :income, :buyer, :fat, :snf, :paid, :notes, :u)
                on conflict (farm_id, record_date, session, product) where animal_id is null
                do update set quantity = excluded.quantity, sold_qty = excluded.sold_qty, rate = excluded.rate,
                    income_amount = excluded.income_amount, buyer_contact_id = excluded.buyer_contact_id,
                    fat_pct = excluded.fat_pct, snf_pct = excluded.snf_pct, paid = excluded.paid,
                    notes = excluded.notes, updated_at = now()
                returning id""")
                .param("f", farmId).param("d", date).param("s", session.name()).param("p", product.name())
                .param("q", quantity).param("unit", product == Product.MANURE ? "KG" : "L")
                .param("sold", soldQty).param("rate", rate).param("income", income).param("buyer", buyerId)
                .param("fat", fat).param("snf", snf).param("paid", paid).param("notes", notes).param("u", userId)
                .query(Long.class).single();
    }

    public int deleteSale(Long farmId, Long id) {
        return jdbc.sql("delete from production_record where farm_id = :f and animal_id is null and id = :id")
                .param("f", farmId).param("id", id).update();
    }

    /** Withheld litres of one cow between two dates (both included). */
    public BigDecimal withheldLitres(Long farmId, Long animalId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                select coalesce(sum(quantity), 0) from production_record
                where farm_id = :f and animal_id = :a and withheld and record_date between :from and :to""")
                .param("f", farmId).param("a", animalId).param("from", from).param("to", to)
                .query(BigDecimal.class).single();
    }

    // ---- mapping

    private static CowInfo cow(ResultSet rs, int i) throws SQLException {
        return new CowInfo(rs.getLong("id"), rs.getString("tag"), rs.getString("name"), rs.getString("breed"),
                (Long) rs.getObject("group_id", Long.class), rs.getObject("last_calving_date", LocalDate.class),
                rs.getObject("date_of_birth", LocalDate.class), rs.getString("sex"), rs.getString("category"),
                rs.getString("status"));
    }

    private static CowRow row(ResultSet rs, int i) throws SQLException {
        return new CowRow(rs.getLong("animal_id"), rs.getObject("record_date", LocalDate.class),
                Session.valueOf(rs.getString("session")), rs.getBigDecimal("quantity"), rs.getBigDecimal("fat_pct"),
                rs.getBigDecimal("snf_pct"), rs.getBigDecimal("protein_pct"), rs.getBigDecimal("density"),
                rs.getObject("scc", Integer.class), rs.getBoolean("withheld"), rs.getString("notes"));
    }

    private static SaleRow sale(ResultSet rs, int i) throws SQLException {
        return new SaleRow(rs.getLong("id"), rs.getObject("record_date", LocalDate.class),
                Session.valueOf(rs.getString("session")), Product.valueOf(rs.getString("product")),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("sold_qty"), rs.getBigDecimal("rate"),
                rs.getBigDecimal("income_amount"), rs.getObject("buyer_contact_id", Long.class),
                rs.getBigDecimal("fat_pct"), rs.getBigDecimal("snf_pct"), rs.getObject("paid", Boolean.class),
                rs.getString("notes"));
    }
}
