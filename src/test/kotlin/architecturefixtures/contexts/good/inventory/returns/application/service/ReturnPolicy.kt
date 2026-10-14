package architecturefixtures.contexts.good.inventory.returns.application.service

import architecturefixtures.contexts.good.inventory.application.model.StockView

// A nested feature belongs to the inventory context, so it may use inventory's public model.
class ReturnPolicy {
    fun restockable(stock: StockView): Int = stock.quantity.value
}
