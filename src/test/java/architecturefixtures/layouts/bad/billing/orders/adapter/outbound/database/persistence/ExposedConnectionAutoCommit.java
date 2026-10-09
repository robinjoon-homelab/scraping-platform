package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class ExposedConnectionAutoCommit {
    public void run(org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection<?> connection) {
        connection.setAutoCommit(false);
    }
}
