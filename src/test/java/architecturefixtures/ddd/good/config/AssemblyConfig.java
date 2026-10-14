package architecturefixtures.ddd.good.config;

// Composition may see every feature, including another feature's aggregate root.
public class AssemblyConfig {
    private final architecturefixtures.ddd.good.billing.orders.application.service.OrderService orders;
    private final architecturefixtures.ddd.good.billing.orders.domain.order.Order sample;

    public AssemblyConfig(architecturefixtures.ddd.good.billing.orders.application.service.OrderService orders, architecturefixtures.ddd.good.billing.orders.domain.order.Order sample) {
        this.orders = orders;
        this.sample = sample;
    }
}
