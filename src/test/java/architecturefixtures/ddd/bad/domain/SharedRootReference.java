package architecturefixtures.ddd.bad.domain;

// The shared root domain is not part of the catalog aggregate package.
public record SharedRootReference(architecturefixtures.ddd.bad.domain.catalog.Product product) {}
