package architecturefixtures.ddd.good.adapter.outbound.persistence;

public class MemoryProductRepository implements architecturefixtures.ddd.good.application.port.output.ProductRepository {
    public void save(architecturefixtures.ddd.good.domain.catalog.Product aggregate) {}

    public java.util.Optional<architecturefixtures.ddd.good.domain.catalog.Product> find(architecturefixtures.ddd.good.domain.catalog.ProductId id) {
        return java.util.Optional.empty();
    }
}
