package architecturefixtures.ddd.bad.billing.orders.application.port.output;

public interface GenericRepository<T> {
    void save(T aggregate);
}
