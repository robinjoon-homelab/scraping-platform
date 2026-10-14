package architecturefixtures.contexts.bad.inventory.application.service

import architecturefixtures.contexts.bad.ordering.application.model.OrderView
import architecturefixtures.contexts.bad.ordering.application.port.input.OrderUseCase

// Calls the other context's use case from the inventory core.
class ForeignUseCaseInCore(
    private val orders: OrderUseCase,
) {
    fun reorder(): String = orders.place(1)
}

// Reads the other context's model in the inventory core.
class ForeignModelInCore {
    fun statusOf(view: OrderView): String = view.status
}
