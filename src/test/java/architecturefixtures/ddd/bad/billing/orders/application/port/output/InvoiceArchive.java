package architecturefixtures.ddd.bad.billing.orders.application.port.output;

public interface InvoiceArchive {
    java.util.Optional<architecturefixtures.ddd.bad.billing.orders.domain.invoice.Invoice> find();
}
