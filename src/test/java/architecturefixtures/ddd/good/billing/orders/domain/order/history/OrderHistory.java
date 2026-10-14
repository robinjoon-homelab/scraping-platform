package architecturefixtures.ddd.good.billing.orders.domain.order.history;

// A subpackage of an aggregate package belongs to that aggregate and may hold its root.
public record OrderHistory(architecturefixtures.ddd.good.billing.orders.domain.order.Order order) {}
