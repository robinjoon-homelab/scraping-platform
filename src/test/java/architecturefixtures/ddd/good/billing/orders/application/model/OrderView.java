package architecturefixtures.ddd.good.billing.orders.application.model;

// A feature-internal model may carry the feature's own root.
public record OrderView(architecturefixtures.ddd.good.billing.orders.domain.order.Order order) {}
