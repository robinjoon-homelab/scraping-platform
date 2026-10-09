package architecturefixtures.layouts.good.billing.orders.adapter.outbound.database.persistence;

public class JdbcReader {
    public ReadOperation action(java.sql.Connection connection) {
        return connection::isClosed;
    }

    public interface ReadOperation {
        boolean run() throws java.sql.SQLException;
    }
}
