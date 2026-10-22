package com.dairyfarm.breeding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Small read queries used by the breeding and calf screens. */
@Repository
public class BreedingSql {

    private final JdbcClient jdbc;

    public BreedingSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Heat dates per animal, oldest first. */
    public Map<Long, List<LocalDate>> heatDates(Long farmId, Collection<Long> animalIds) {
        Map<Long, List<LocalDate>> out = new HashMap<>();
        if (animalIds.isEmpty()) {
            return out;
        }
        jdbc.sql("""
                select animal_id, event_date from farm_event
                where farm_id = :farm and event_type = 'HEAT' and animal_id in (:ids) order by event_date, id""")
                .param("farm", farmId).param("ids", animalIds)
                .query((rs, n) -> {
                    out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getObject(2, LocalDate.class));
                    return null;
                }).list();
        return out;
    }

    /** Average litres per milked day over [from, to], per cow. */
    public Map<Long, BigDecimal> milkPerDay(Long farmId, Collection<Long> animalIds, LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> out = new HashMap<>();
        if (animalIds.isEmpty()) {
            return out;
        }
        jdbc.sql("""
                select animal_id, sum(quantity) / nullif(count(distinct record_date), 0) from production_record
                where farm_id = :farm and animal_id in (:ids) and record_date between :from and :to and quantity is not null
                group by animal_id""")
                .param("farm", farmId).param("ids", animalIds).param("from", from).param("to", to)
                .query((rs, n) -> {
                    BigDecimal v = rs.getBigDecimal(2);
                    if (v != null) {
                        out.put(rs.getLong(1), v.setScale(1, RoundingMode.HALF_UP));
                    }
                    return null;
                }).list();
        return out;
    }

    /** Tags on the farm (for the next free tag). */
    public List<String> tags(Long farmId) {
        return jdbc.sql("select tag from animal where farm_id = :farm").param("farm", farmId).query(String.class).list();
    }
}
