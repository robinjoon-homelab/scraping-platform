package architecturefixtures.contexts.bad.application.service

import architecturefixtures.contexts.bad.ordering.application.port.input.OrderUseCase

// Shared root application code that calls a context's use case is no longer feature independent.
class BusinessAwareService(
    private val orders: OrderUseCase,
) {
    fun placeOne(): String = orders.place(1)
}
