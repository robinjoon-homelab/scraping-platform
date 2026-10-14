package architecturefixtures.ddd.bad.billing.orders.domain.order.history;

// A subpackage belongs to the order aggregate, so it cannot designate another aggregate.
@architecturefixtures.ddd.bad.domain.AggregateRoot
public final class NestedAggregateRoot {
    private NestedAggregateRoot() {}
}
