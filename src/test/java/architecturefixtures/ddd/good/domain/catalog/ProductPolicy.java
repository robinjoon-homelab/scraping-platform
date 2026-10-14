package architecturefixtures.ddd.good.domain.catalog;

// A helper in the aggregate package belongs to the aggregate and may use its root.
public final class ProductPolicy {
    public boolean isListed(Product product) {
        return product != null;
    }
}
