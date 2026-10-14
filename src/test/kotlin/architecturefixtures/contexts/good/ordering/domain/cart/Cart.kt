package architecturefixtures.contexts.good.ordering.domain.cart

import architecturefixtures.contexts.good.domain.AggregateRoot

@AggregateRoot
class Cart private constructor(
    val id: CartId,
    val quantity: Int,
) {
    companion object {
        fun open(
            id: CartId,
            quantity: Int,
        ): Cart = Cart(id, quantity)
    }
}
