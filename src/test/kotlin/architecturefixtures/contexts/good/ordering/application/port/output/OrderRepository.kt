package architecturefixtures.contexts.good.ordering.application.port.output

import architecturefixtures.contexts.good.ordering.domain.order.Order

interface OrderRepository {
    fun save(order: Order)
}
