package architecturefixtures.contexts.bad.ordering.domain.cart

import architecturefixtures.contexts.bad.ordering.domain.order.Order

// Holds the order root instead of its id. Being in the same context does not open another aggregate's root.
class CartHoldingOrder(
    val order: Order,
)
