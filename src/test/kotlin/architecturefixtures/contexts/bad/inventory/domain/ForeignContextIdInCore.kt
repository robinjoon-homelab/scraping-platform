package architecturefixtures.contexts.bad.inventory.domain

import architecturefixtures.contexts.bad.ordering.domain.order.OrderId

// Even an ordinary id of another context's domain stays outside the inventory core.
class ForeignContextIdInCore(
    val order: OrderId,
)
