package architecturefixtures.contexts.good.ordering.application.port.output

import architecturefixtures.contexts.good.ordering.domain.cart.Cart
import architecturefixtures.contexts.good.ordering.domain.cart.CartId

interface CartRepository {
    fun findById(id: CartId): Cart?

    fun save(cart: Cart)
}
