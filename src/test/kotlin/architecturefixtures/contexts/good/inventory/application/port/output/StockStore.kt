package architecturefixtures.contexts.good.inventory.application.port.output

import architecturefixtures.contexts.good.inventory.application.model.StockView

interface StockStore {
    fun find(sku: String): StockView?

    fun save(stock: StockView)
}
