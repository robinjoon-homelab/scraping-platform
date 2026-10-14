package architecturefixtures.contexts.bad.ordering.domain.order

import architecturefixtures.contexts.bad.domain.AggregateRoot

@AggregateRoot
class Order private constructor(
    val id: OrderId,
) {
    companion object {
        fun place(id: OrderId): Order = Order(id)
    }
}
