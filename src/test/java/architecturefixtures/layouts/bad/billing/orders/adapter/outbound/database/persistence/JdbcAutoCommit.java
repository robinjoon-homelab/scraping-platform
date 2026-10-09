package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class JdbcAutoCommit {
    public void run(java.sql.Connection connection) throws java.sql.SQLException {
        connection.setAutoCommit(false);
    }
}
