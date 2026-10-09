package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class ExposedCommitReference {
    public Runnable action(org.jetbrains.exposed.v1.jdbc.JdbcTransaction transaction) {
        return transaction::commit;
    }
}
