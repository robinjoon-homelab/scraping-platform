package architecturefixtures.layouts.good.billing.orders.adapter.outbound.database.persistence;

public class ExposedConnectionReader {
    public java.util.function.BooleanSupplier action(org.jetbrains.exposed.v1.jdbc.JdbcTransaction transaction) {
        return transaction.getConnection()::getAutoCommit;
    }
}
