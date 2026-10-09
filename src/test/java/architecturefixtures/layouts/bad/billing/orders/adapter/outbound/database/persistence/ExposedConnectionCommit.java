package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class ExposedConnectionCommit {
    public void run(org.jetbrains.exposed.v1.jdbc.JdbcTransaction transaction) {
        transaction.getConnection().commit();
    }
}
