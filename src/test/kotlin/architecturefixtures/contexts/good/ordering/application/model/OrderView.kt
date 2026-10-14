package architecturefixtures.contexts.good.ordering.application.model

import architecturefixtures.contexts.good.application.model.Quantity

data class OrderView(
    val orderId: String,
    val quantity: Quantity,
)
