package architecturefixtures.contexts.bad.ordering.domain.order.history

import architecturefixtures.contexts.bad.domain.AggregateRoot

// A subpackage belongs to the order aggregate, so a root here cannot designate another aggregate.
@AggregateRoot
class RootNestedInAggregate private constructor(
    val id: String,
) {
    companion object {
        fun create(id: String): RootNestedInAggregate = RootNestedInAggregate(id)
    }
}
