package architecturefixtures.ddd.bad.billing.orders.domain;

// A root directly in the feature's domain package would sit above every aggregate package of the feature.
@architecturefixtures.ddd.bad.domain.AggregateRoot
public final class FeatureDomainRoot {
    private FeatureDomainRoot() {}
}
