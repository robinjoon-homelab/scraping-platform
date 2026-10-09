package architecturefixtures.layouts.bad.migration;

public class UnlistedJdbcMigration extends org.flywaydb.core.api.migration.BaseJavaMigration {
    private java.sql.Connection connection;
    public void migrate(org.flywaydb.core.api.migration.Context context) { connection = context.getConnection(); }
}
