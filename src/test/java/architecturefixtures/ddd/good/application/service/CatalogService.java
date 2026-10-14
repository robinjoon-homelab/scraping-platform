package architecturefixtures.ddd.good.application.service;

public class CatalogService {
    private final architecturefixtures.ddd.good.application.port.output.ProductRepository products;

    public CatalogService(architecturefixtures.ddd.good.application.port.output.ProductRepository products) {
        this.products = products;
    }

    public void list(architecturefixtures.ddd.good.domain.catalog.Product product) {
        products.save(product);
    }
}
