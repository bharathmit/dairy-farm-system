package com.dairyfarm.finance;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.dairyfarm.finance.FinanceViews.AllocKind;
import com.dairyfarm.finance.FinanceViews.Category;
import com.dairyfarm.finance.FinanceViews.Direction;
import com.dairyfarm.finance.FinanceViews.Status;
import com.dairyfarm.feed.FeedViews.PaymentMethod;

/** finance_entry reads and writes (JdbcClient). */
@Repository
public class FinanceRepository {

    /** One finance_entry row with the names needed for the response. */
    public record Row(Long id, Direction direction, Category category, BigDecimal amount, LocalDate date, LocalTime time,
                      Status status, PaymentMethod paymentMethod, String counterparty, Long contactId, String contactName,
                      Long memberId, String memberName, LocalDate periodFrom, LocalDate periodTo, boolean advance,
                      BigDecimal advanceDeducted, AllocKind allocKind, Long groupId, String groupName, Long animalId,
                      String animalTag, String animalName, BigDecimal quantity, String unit, String sourceType,
                      Long sourceId, String sourcePart, String description, boolean auto, String notes,
                      Long txnItemId, String txnType) {
    }

    /** Fields for a new or replaced manual entry. */
    public record Write(Direction direction, Category category, BigDecimal amount, LocalDate date, LocalTime time,
                        Status status, PaymentMethod paymentMethod, String counterparty, Long contactId, Long memberId,
                        LocalDate periodFrom, LocalDate periodTo, boolean advance, BigDecimal advanceDeducted,
                        AllocKind allocKind, Long groupId, Long animalId, BigDecimal quantity, String unit, String notes) {
    }

    private static final String SELECT = """
            select e.*, c.name as contact_name, m.name as member_name, g.name as group_name,
                   a.tag as animal_tag, a.name as animal_name, t.item_id as txn_item_id, t.txn_type as txn_type
            from finance_entry e
            left join item_txn t on e.source_type = 'ITEM_TXN' and t.id = e.source_id
            left join contact c on c.id = e.contact_id
            left join farm_member m on m.id = e.member_id
            left join animal_group g on g.id = e.group_id
            left join animal a on a.id = e.animal_id
            """;

    private final JdbcClient jdbc;

    public FinanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Row> find(Long farmId, Long id) {
        return jdbc.sql(SELECT + " where e.farm_id = :farm and e.id = :id").param("farm", farmId).param("id", id)
                .query(FinanceRepository::row).optional();
    }

    /** Entries newest first, filtered; total count for paging. */
    public List<Row> list(Long farmId, String where, Map<String, Object> params, int limit, long offset) {
        var q = jdbc.sql(SELECT + " where e.farm_id = :farm " + where
                + " order by e.entry_date desc, e.entry_time desc nulls last, e.id desc limit :limit offset :offset")
                .param("farm", farmId).param("limit", limit).param("offset", offset);
        for (var p : params.entrySet()) {
            q = q.param(p.getKey(), p.getValue());
        }
        return q.query(FinanceRepository::row).list();
    }

    public long count(Long farmId, String where, Map<String, Object> params) {
        var q = jdbc.sql("select count(*) from finance_entry e where e.farm_id = :farm " + where).param("farm", farmId);
        for (var p : params.entrySet()) {
            q = q.param(p.getKey(), p.getValue());
        }
        return q.query(Long.class).single();
    }

    public Long insert(Long farmId, Write w, UUID userId) {
        return jdbc.sql("""
                insert into finance_entry (farm_id, direction, category, amount, entry_date, entry_time, status,
                  payment_method, counterparty, contact_id, member_id, period_from, period_to, advance, advance_deducted,
                  alloc_kind, group_id, animal_id, quantity, unit, notes, auto, created_by)
                values (:farm, :dir, :cat, :amount, :date, :time, :status, :pm, :cp, :contact, :member, :pf, :pt,
                  :adv, :advd, :alloc, :grp, :animal, :qty, :unit, :notes, false, :user)
                returning id""")
                .param("farm", farmId).params(params(w)).param("user", userId)
                .query(Long.class).single();
    }

    public void update(Long id, Write w) {
        jdbc.sql("""
                update finance_entry set direction = :dir, category = :cat, amount = :amount, entry_date = :date,
                  entry_time = :time, status = :status, payment_method = :pm, counterparty = :cp, contact_id = :contact,
                  member_id = :member, period_from = :pf, period_to = :pt, advance = :adv, advance_deducted = :advd,
                  alloc_kind = :alloc, group_id = :grp, animal_id = :animal, quantity = :qty, unit = :unit,
                  notes = :notes, updated_at = now()
                where id = :id""").params(params(w)).param("id", id).update();
    }

    /** What may change on an automatic entry. */
    public void updateAuto(Long id, Status status, PaymentMethod method, String notes) {
        jdbc.sql("update finance_entry set status = :s, payment_method = :pm, notes = :n, updated_at = now() where id = :id")
                .param("s", status.name()).param("pm", method == null ? null : method.name()).param("n", notes)
                .param("id", id).update();
    }

    public void delete(Long id) {
        jdbc.sql("delete from finance_entry where id = :id").param("id", id).update();
    }

    /** Writes an entry's paid status back to its source record (sources with their own paid flag). */
    public void writeBackStatus(Row r, Status status, PaymentMethod method) {
        boolean paid = status == Status.PAID;
        switch (r.sourceType()) {
            case "MILK_SALE" -> jdbc.sql("update production_record set paid = :p, updated_at = now() where id = :id")
                    .param("p", paid).param("id", r.sourceId()).update();
            case "ITEM_TXN" -> jdbc.sql("update item_txn set paid = :p, payment_method = :pm, updated_at = now() where id = :id")
                    .param("p", paid).param("pm", method == null ? null : method.name()).param("id", r.sourceId()).update();
            case "BREEDING" -> {
                if (!"PD".equals(r.sourcePart())) {
                    jdbc.sql("update breeding_record set fee_paid = :p, updated_at = now() where id = :id")
                            .param("p", paid).param("id", r.sourceId()).update();
                }
            }
            default -> { }
        }
    }

    /** Sums by direction and category in a period. */
    public Map<Category, BigDecimal> byCategory(Long farmId, LocalDate from, LocalDate to) {
        Map<Category, BigDecimal> out = new HashMap<>();
        jdbc.sql("""
                select category, sum(amount) as total from finance_entry
                where farm_id = :farm and entry_date between :from and :to group by category""")
                .param("farm", farmId).param("from", from).param("to", to)
                .query((rs, n) -> out.put(Category.valueOf(rs.getString("category")), rs.getBigDecimal("total"))).list();
        return out;
    }

    public FinanceViews.Pending pending(Long farmId) {
        return jdbc.sql("select count(*) as n, coalesce(sum(amount), 0) as total from finance_entry "
                        + "where farm_id = :farm and status = 'PENDING'")
                .param("farm", farmId)
                .query((rs, n) -> new FinanceViews.Pending(rs.getLong("n"), rs.getBigDecimal("total"))).single();
    }

    public Optional<LocalDate> firstDate(Long farmId) {
        return jdbc.sql("select min(entry_date) from finance_entry where farm_id = :farm").param("farm", farmId)
                .query(LocalDate.class).optional();
    }

    static Map<String, Object> params(Write w) {
        Map<String, Object> p = new HashMap<>();
        p.put("dir", w.direction().name());
        p.put("cat", w.category().name());
        p.put("amount", w.amount());
        p.put("date", w.date());
        p.put("time", w.time());
        p.put("status", w.status().name());
        p.put("pm", w.paymentMethod() == null ? null : w.paymentMethod().name());
        p.put("cp", w.counterparty());
        p.put("contact", w.contactId());
        p.put("member", w.memberId());
        p.put("pf", w.periodFrom());
        p.put("pt", w.periodTo());
        p.put("adv", w.advance());
        p.put("advd", w.advanceDeducted());
        p.put("alloc", w.allocKind().name());
        p.put("grp", w.groupId());
        p.put("animal", w.animalId());
        p.put("qty", w.quantity());
        p.put("unit", w.unit());
        p.put("notes", w.notes());
        return p;
    }

    static Row row(ResultSet rs, int n) throws SQLException {
        String pm = rs.getString("payment_method");
        return new Row(rs.getLong("id"), Direction.valueOf(rs.getString("direction")),
                Category.valueOf(rs.getString("category")), rs.getBigDecimal("amount"),
                rs.getObject("entry_date", LocalDate.class), rs.getObject("entry_time", LocalTime.class),
                Status.valueOf(rs.getString("status")), pm == null ? null : PaymentMethod.valueOf(pm),
                rs.getString("counterparty"), rs.getObject("contact_id", Long.class), rs.getString("contact_name"),
                rs.getObject("member_id", Long.class), rs.getString("member_name"),
                rs.getObject("period_from", LocalDate.class), rs.getObject("period_to", LocalDate.class),
                rs.getBoolean("advance"), rs.getBigDecimal("advance_deducted"),
                AllocKind.valueOf(rs.getString("alloc_kind")), rs.getObject("group_id", Long.class),
                rs.getString("group_name"), rs.getObject("animal_id", Long.class), rs.getString("animal_tag"),
                rs.getString("animal_name"), rs.getBigDecimal("quantity"), rs.getString("unit"),
                rs.getString("source_type"), rs.getObject("source_id", Long.class), rs.getString("source_part"),
                rs.getString("description"), rs.getBoolean("auto"), rs.getString("notes"),
                rs.getObject("txn_item_id", Long.class), rs.getString("txn_type"));
    }
}
