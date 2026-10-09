package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class JdbcReleaseSavepoint {
    public void run(java.sql.Connection connection) throws java.sql.SQLException {
        connection.releaseSavepoint(null);
    }
}
