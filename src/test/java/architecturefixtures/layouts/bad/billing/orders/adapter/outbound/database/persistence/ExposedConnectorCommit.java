package architecturefixtures.layouts.bad.billing.orders.adapter.outbound.database.persistence;

public class ExposedConnectorCommit {
    public void run(org.jetbrains.exposed.v1.jdbc.Database database) {
        database.getConnector().invoke().commit();
    }
}
