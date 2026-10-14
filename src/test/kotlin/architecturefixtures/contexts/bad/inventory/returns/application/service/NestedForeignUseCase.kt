package architecturefixtures.contexts.bad.inventory.returns.application.service

import architecturefixtures.contexts.bad.ordering.application.port.input.OrderUseCase

// A nested feature still belongs to the inventory context, so the sub-package does not exempt the call.
class NestedForeignUseCase(
    private val orders: OrderUseCase,
) {
    fun replace(): String = orders.place(1)
}
