package architecturefixtures.ddd.bad.billing.orders.application.port.output;

// Both roots are reachable only through inherited signatures.
public interface InheritedMixedRepository extends OrderRepository, InvoiceArchive {}
