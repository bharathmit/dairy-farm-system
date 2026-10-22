package com.dairyfarm.finance;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * Keeps a farm's automatic finance entries in step with their source records. Source services call
 * {@link #sync(Long)} at the end of every change, inside their own transaction, so entries and sources commit together.
 */
@Component
public class FinancePoster {

    private static final List<String> FARM_SQL = FinanceSql.statements(false);

    private final JdbcClient jdbc;
    private final EntityManager em;

    public FinancePoster(JdbcClient jdbc, EntityManager em) {
        this.jdbc = jdbc;
        this.em = em;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void sync(Long farmId) {
        em.flush();
        for (String sql : FARM_SQL) {
            jdbc.sql(sql).param("farm", farmId).update();
        }
    }
}
