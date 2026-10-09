package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class JdbcReadOnly {
    public void run(java.sql.Connection connection) throws java.sql.SQLException {
        connection.setReadOnly(true);
    }
}
