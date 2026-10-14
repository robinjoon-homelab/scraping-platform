package architecturefixtures.contexts.good.inventory.application.service

import architecturefixtures.contexts.good.application.model.Quantity
import architecturefixtures.contexts.good.inventory.application.model.StockView
import architecturefixtures.contexts.good.inventory.application.port.input.StockUseCase
import architecturefixtures.contexts.good.inventory.application.port.output.StockStore

// The inventory core never names ordering. Both contexts share only root types.
class StockService(
    private val stock: StockStore,
) : StockUseCase {
    override fun restock(
        sku: String,
        quantity: Quantity,
    ): StockView {
        val current = stock.find(sku)?.quantity?.value ?: 0
        val restocked = StockView(sku, Quantity(current + quantity.value))
        stock.save(restocked)
        return restocked
    }
}
