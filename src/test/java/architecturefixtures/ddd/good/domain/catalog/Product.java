package architecturefixtures.ddd.good.domain.catalog;

// Layer-first aggregate: the exact package domain.catalog is its boundary.
@architecturefixtures.ddd.good.domain.AggregateRoot
public final class Product {
    private final ProductId id;
    private final architecturefixtures.ddd.good.domain.Money price;

    private Product(ProductId id, architecturefixtures.ddd.good.domain.Money price) {
        this.id = id;
        this.price = price;
    }

    public static Product list(ProductId id, architecturefixtures.ddd.good.domain.Money price) {
        return new Product(id, price);
    }
}
