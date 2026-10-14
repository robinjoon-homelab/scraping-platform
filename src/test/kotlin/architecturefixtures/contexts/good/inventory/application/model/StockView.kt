package architecturefixtures.contexts.good.inventory.application.model

import architecturefixtures.contexts.good.application.model.Quantity

data class StockView(
    val sku: String,
    val quantity: Quantity,
)
