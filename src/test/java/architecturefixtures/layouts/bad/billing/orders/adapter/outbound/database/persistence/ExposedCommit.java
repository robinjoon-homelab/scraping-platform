package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class ExposedCommit {
    public void run(org.jetbrains.exposed.v1.jdbc.JdbcTransaction transaction) {
        transaction.commit();
    }
}
