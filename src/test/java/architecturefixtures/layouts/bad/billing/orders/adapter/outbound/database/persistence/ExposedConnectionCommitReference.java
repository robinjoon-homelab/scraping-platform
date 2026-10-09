package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class ExposedConnectionCommitReference {
    public Runnable action(org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection<?> connection) {
        return connection::commit;
    }
}
