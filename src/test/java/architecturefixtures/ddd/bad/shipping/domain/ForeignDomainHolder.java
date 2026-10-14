package architecturefixtures.ddd.bad.shipping.domain;

public record ForeignDomainHolder(architecturefixtures.ddd.bad.billing.orders.domain.order.OrderId order) {}
