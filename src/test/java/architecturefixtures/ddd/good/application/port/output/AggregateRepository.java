package architecturefixtures.ddd.good.application.port.output;

// A shared generic base refers to no root until a repository binds its type argument.
public interface AggregateRepository<T> {
    void save(T aggregate);
}
