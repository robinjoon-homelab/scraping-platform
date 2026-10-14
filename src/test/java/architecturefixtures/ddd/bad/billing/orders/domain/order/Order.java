package architecturefixtures.ddd.bad.billing.orders.domain.order;

@architecturefixtures.ddd.bad.domain.AggregateRoot
public final class Order {
    private final OrderId id;

    private Order(OrderId id) {
        this.id = id;
    }
}
