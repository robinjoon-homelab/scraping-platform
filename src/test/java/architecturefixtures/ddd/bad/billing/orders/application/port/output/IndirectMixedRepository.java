package architecturefixtures.ddd.bad.billing.orders.application.port.output;

// Order comes from the inherited generic argument and Invoice from a generic return type.
public interface IndirectMixedRepository extends OrderRepository {
    java.util.List<architecturefixtures.ddd.bad.billing.orders.domain.invoice.Invoice> invoices();
}
