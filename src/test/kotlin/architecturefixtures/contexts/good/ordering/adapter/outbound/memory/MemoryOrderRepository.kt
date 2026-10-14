package architecturefixtures.contexts.good.ordering.adapter.outbound.memory

import architecturefixtures.contexts.good.ordering.application.port.output.OrderRepository
import architecturefixtures.contexts.good.ordering.domain.order.Order
import architecturefixtures.contexts.good.ordering.domain.order.OrderId

class MemoryOrderRepository : OrderRepository {
    private val orders = mutableMapOf<OrderId, Order>()

    override fun save(order: Order) {
        orders[order.id] = order
    }
}
