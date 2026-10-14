package architecturefixtures.ddd.bad.billing.orders.domain.invoice;

public final class NestedRootReference {
    public static final class NestedRootHolder {
        private final architecturefixtures.ddd.bad.billing.orders.domain.order.Order order;

        public NestedRootHolder(architecturefixtures.ddd.bad.billing.orders.domain.order.Order order) {
            this.order = order;
        }
    }
}
