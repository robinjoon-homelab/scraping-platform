package architecturefixtures.ddd.bad.billing.orders.application.port.output;

public interface DoubledRootRepository {
    void save(architecturefixtures.ddd.bad.billing.orders.domain.doubled.DoubledRoot root);
}
