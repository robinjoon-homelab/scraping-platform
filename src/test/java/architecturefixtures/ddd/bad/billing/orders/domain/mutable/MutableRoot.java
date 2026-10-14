package architecturefixtures.ddd.bad.billing.orders.domain.mutable;

@architecturefixtures.ddd.bad.domain.AggregateRoot
public final class MutableRoot {
    private String status;

    private MutableRoot(String status) {
        this.status = status;
    }
}
