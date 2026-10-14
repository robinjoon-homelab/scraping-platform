package architecturefixtures.contexts.good.ordering.application.service

import architecturefixtures.contexts.good.application.model.Quantity
import architecturefixtures.contexts.good.application.port.output.IdGenerator
import architecturefixtures.contexts.good.ordering.application.model.OrderView
import architecturefixtures.contexts.good.ordering.application.port.input.OrderUseCase
import architecturefixtures.contexts.good.ordering.application.port.output.CartRepository
import architecturefixtures.contexts.good.ordering.application.port.output.OrderRepository
import architecturefixtures.contexts.good.ordering.domain.cart.CartId
import architecturefixtures.contexts.good.ordering.domain.order.Order
import architecturefixtures.contexts.good.ordering.domain.order.OrderId

// The ordering core uses both of its aggregates through its own repositories and root shared types only.
class OrderService(
    private val orders: OrderRepository,
    private val carts: CartRepository,
    private val ids: IdGenerator,
) : OrderUseCase {
    override fun place(cartId: String): OrderView? {
        val cart = carts.findById(CartId(cartId)) ?: return null
        val order = Order.place(OrderId(ids.next()), cart.id, cart.quantity)
        orders.save(order)
        return OrderView(order.id.value, Quantity(order.quantity))
    }
}
