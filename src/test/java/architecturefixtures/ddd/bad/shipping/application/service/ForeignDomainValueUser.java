package architecturefixtures.ddd.bad.shipping.application.service;

// The generic feature rule allows foreign domain values. The DDD rule keeps them private to their feature.
public class ForeignDomainValueUser {
    private final architecturefixtures.ddd.bad.billing.orders.domain.order.OrderId order;

    public ForeignDomainValueUser(architecturefixtures.ddd.bad.billing.orders.domain.order.OrderId order) {
        this.order = order;
    }
}
