package architecturefixtures.contexts.good.inventory.adapter.outbound.memory

import architecturefixtures.contexts.good.inventory.application.model.StockView
import architecturefixtures.contexts.good.inventory.application.port.output.StockStore

class MemoryStockStore : StockStore {
    private val items = mutableMapOf<String, StockView>()

    override fun find(sku: String): StockView? = items[sku]

    override fun save(stock: StockView) {
        items[stock.sku] = stock
    }
}
