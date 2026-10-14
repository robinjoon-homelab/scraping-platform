package architecturefixtures.contexts.good.ordering.domain.order

import architecturefixtures.contexts.good.domain.AggregateRoot
import architecturefixtures.contexts.good.ordering.domain.cart.CartId

// A second aggregate of the ordering context. It refers to the cart by id and never holds the Cart root.
@AggregateRoot
class Order private constructor(
    val id: OrderId,
    val cart: CartId,
    val quantity: Int,
) {
    companion object {
        fun place(
            id: OrderId,
            cart: CartId,
            quantity: Int,
        ): Order = Order(id, cart, quantity)
    }
}
