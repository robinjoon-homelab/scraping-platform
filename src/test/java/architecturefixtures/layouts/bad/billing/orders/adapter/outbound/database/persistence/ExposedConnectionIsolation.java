package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class ExposedConnectionIsolation {
    public void run(org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection<?> connection) {
        connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_SERIALIZABLE);
    }
}
