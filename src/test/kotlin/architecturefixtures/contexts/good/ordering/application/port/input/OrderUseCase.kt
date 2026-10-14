package architecturefixtures.contexts.good.ordering.application.port.input

import architecturefixtures.contexts.good.ordering.application.model.OrderView

interface OrderUseCase {
    fun place(cartId: String): OrderView?
}
