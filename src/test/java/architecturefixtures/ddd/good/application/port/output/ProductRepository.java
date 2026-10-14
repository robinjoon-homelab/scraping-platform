package architecturefixtures.ddd.good.application.port.output;

public interface ProductRepository extends AggregateRepository<architecturefixtures.ddd.good.domain.catalog.Product> {
    java.util.Optional<architecturefixtures.ddd.good.domain.catalog.Product> find(architecturefixtures.ddd.good.domain.catalog.ProductId id);
}
