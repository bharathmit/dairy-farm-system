package com.dairyfarm.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Stock movements (item_txn) and stock on hand. */
@Repository
public class StockRepository {

    public enum TxnType { PURCHASE, USAGE, ADJUSTMENT, SERVICE, PRODUCE }

    public record Level(BigDecimal onHand, LocalDate lastTxnDate) {
    }

    private static final String ON_HAND = """
            coalesce(sum(case when txn_type in ('PURCHASE','PRODUCE') then quantity
                              when txn_type = 'USAGE' then -quantity
                              when txn_type = 'ADJUSTMENT' then quantity else 0 end), 0)""";

    private final JdbcClient jdbc;

    public StockRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void add(Long farmId, Long itemId, TxnType type, LocalDate date, LocalTime time, BigDecimal quantity,
                    BigDecimal cost, Long supplierId, Long animalId, String sourceType, Long sourceId, String notes) {
        jdbc.sql("""
                insert into item_txn (farm_id, item_id, txn_type, txn_date, txn_time, quantity, cost, supplier_contact_id,
                                      animal_id, source_type, source_id, notes)
                values (:farm, :item, :type, :date, :time, :qty, :cost, :supplier, :animal, :stype, :sid, :notes)""")
                .param("farm", farmId).param("item", itemId).param("type", type.name()).param("date", date)
                .param("time", time).param("qty", quantity).param("cost", cost).param("supplier", supplierId)
                .param("animal", animalId).param("stype", sourceType).param("sid", sourceId).param("notes", notes)
                .update();
    }

    /** Removes the movements written for a source record (e.g. the straws used by a service). */
    public int removeForSource(Long farmId, String sourceType, Long sourceId) {
        return jdbc.sql("delete from item_txn where farm_id = :farm and source_type = :t and source_id = :id")
                .param("farm", farmId).param("t", sourceType).param("id", sourceId).update();
    }

    public BigDecimal onHand(Long itemId) {
        return jdbc.sql("select " + ON_HAND + " from item_txn where item_id = :id").param("id", itemId)
                .query(BigDecimal.class).single();
    }

    public Map<Long, Level> levels(Collection<Long> itemIds) {
        Map<Long, Level> out = new HashMap<>();
        if (itemIds.isEmpty()) {
            return out;
        }
        jdbc.sql("select item_id, " + ON_HAND + " as on_hand, max(txn_date) as last_date from item_txn "
                        + "where item_id in (:ids) group by item_id")
                .param("ids", itemIds)
                .query((rs, n) -> {
                    out.put(rs.getLong("item_id"), new Level(rs.getBigDecimal("on_hand"),
                            rs.getObject("last_date", LocalDate.class)));
                    return null;
                }).list();
        return out;
    }

    public boolean usedOnRecords(Long itemId) {
        return jdbc.sql("select exists (select 1 from breeding_record where semen_item_id = :id)")
                .param("id", itemId).query(Boolean.class).single();
    }
}
