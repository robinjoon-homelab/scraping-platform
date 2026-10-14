package architecturefixtures.ddd.good.billing.orders.application.port.output;

// The root is referenced only through the inherited generic signature.
public interface OrderRepository extends architecturefixtures.ddd.good.application.port.output.AggregateRepository<architecturefixtures.ddd.good.billing.orders.domain.order.Order> {}
