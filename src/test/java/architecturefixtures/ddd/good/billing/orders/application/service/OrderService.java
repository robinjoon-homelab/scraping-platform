package architecturefixtures.ddd.good.billing.orders.application.service;

public class OrderService {
    private final architecturefixtures.ddd.good.billing.orders.application.port.output.OrderRepository orders;

    public OrderService(architecturefixtures.ddd.good.billing.orders.application.port.output.OrderRepository orders) {
        this.orders = orders;
    }

    public void place(architecturefixtures.ddd.good.billing.orders.domain.order.Order order) {
        orders.save(order);
    }
}
