package architecturefixtures.ddd.bad.billing.orders.application.port.output;

public interface MixedRepository {
    void save(architecturefixtures.ddd.bad.billing.orders.domain.order.Order order);

    void save(architecturefixtures.ddd.bad.billing.orders.domain.invoice.Invoice invoice);
}
