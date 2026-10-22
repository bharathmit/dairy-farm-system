package db.migration;

import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.dairyfarm.finance.FinanceSql;

/** Creates automatic finance entries for the records saved before the Finance module existed. */
public class V6_1__Finance_backfill extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement st = context.getConnection().createStatement()) {
            for (String sql : FinanceSql.statements(true)) {
                st.execute(sql);
            }
        }
    }
}
