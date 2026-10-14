package architecturefixtures.ddd.good.billing.orders.application.model;

// A public model of scalar values that another feature may read instead of the billing domain.
public record OrderSummary(java.util.UUID orderId, long totalCents) {}
