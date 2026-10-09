package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class JdbcCommitReference {
    public Operation action(java.sql.Connection connection) {
        return connection::commit;
    }

    public interface Operation {
        void run() throws java.sql.SQLException;
    }
}
