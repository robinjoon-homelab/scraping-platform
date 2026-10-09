package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class JdbcIsolation {
    public void run(java.sql.Connection connection) throws java.sql.SQLException {
        connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_SERIALIZABLE);
    }
}
