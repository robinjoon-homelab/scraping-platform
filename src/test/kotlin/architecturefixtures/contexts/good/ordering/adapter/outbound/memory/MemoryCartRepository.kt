package architecturefixtures.contexts.good.ordering.adapter.outbound.memory

import architecturefixtures.contexts.good.ordering.application.port.output.CartRepository
import architecturefixtures.contexts.good.ordering.domain.cart.Cart
import architecturefixtures.contexts.good.ordering.domain.cart.CartId

class MemoryCartRepository : CartRepository {
    private val carts = mutableMapOf<CartId, Cart>()

    override fun findById(id: CartId): Cart? = carts[id]

    override fun save(cart: Cart) {
        carts[cart.id] = cart
    }
}
