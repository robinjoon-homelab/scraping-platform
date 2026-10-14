package architecturefixtures.contexts.good.ordering.domain.order.history

import architecturefixtures.contexts.good.ordering.domain.order.Order

// A helper subpackage belongs to the order aggregate, so it may use the root.
class OrderHistory {
    fun entryFor(order: Order): String = "${order.id.value}:${order.quantity}"
}
