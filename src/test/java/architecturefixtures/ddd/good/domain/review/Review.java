package architecturefixtures.ddd.good.domain.review;

// A second layer-first aggregate refers to the catalog aggregate by identity only.
@architecturefixtures.ddd.good.domain.AggregateRoot
public final class Review {
    private final architecturefixtures.ddd.good.domain.catalog.ProductId product;

    private Review(architecturefixtures.ddd.good.domain.catalog.ProductId product) {
        this.product = product;
    }

    public static Review of(architecturefixtures.ddd.good.domain.catalog.ProductId product) {
        return new Review(product);
    }
}
