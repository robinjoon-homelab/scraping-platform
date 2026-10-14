package architecturefixtures.ddd.bad.billing.orders.application.port.output;

// Any output port that exposes a root acts as its repository regardless of the role name.
public interface DoubledRootStore {
    java.util.Optional<architecturefixtures.ddd.bad.billing.orders.domain.doubled.DoubledRoot> load();
}
