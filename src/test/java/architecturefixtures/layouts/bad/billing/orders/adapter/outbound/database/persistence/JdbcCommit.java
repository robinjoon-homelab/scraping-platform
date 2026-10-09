package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class JdbcCommit {
    public void run(java.sql.Connection connection) throws java.sql.SQLException {
        connection.commit();
    }
}
