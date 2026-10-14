package architecturefixtures.contexts.good.inventory.application.port.input

import architecturefixtures.contexts.good.application.model.Quantity
import architecturefixtures.contexts.good.inventory.application.model.StockView

interface StockUseCase {
    fun restock(
        sku: String,
        quantity: Quantity,
    ): StockView
}
