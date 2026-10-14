package architecturefixtures.ddd.bad.shipping.application.service;

// A layer-first root in the shared domain still belongs to the root feature's application and adapters.
public class SharedRootForeignUser {
    public void use(architecturefixtures.ddd.bad.domain.catalog.Product product) {}
}
