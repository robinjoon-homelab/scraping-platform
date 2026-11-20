package com.example.template.inventory.application.service

import com.example.template.inventory.application.port.input.StockItemNotFoundException
import com.example.template.inventory.application.port.input.StockUseCase
import com.example.template.inventory.application.port.output.StockItemRepository
import com.example.template.inventory.domain.stock.Sku
import com.example.template.inventory.domain.stock.StockItem
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class StockService(
    private val repository: StockItemRepository,
) : StockUseCase {
    // The stored SKU key alone decides uniqueness, so a sequential and a concurrent duplicate fail the same way.
    @Transactional
    override fun register(
        sku: Sku,
        onHand: Int,
    ): StockItem = repository.add(StockItem.register(sku, onHand))

    // Locks the item row, so concurrent restocks each add to the latest onHand instead of overwriting each other.
    @Transactional
    override fun restock(
        sku: Sku,
        quantity: Int,
    ): StockItem {
        val item = repository.findBySkuForUpdate(sku) ?: throw StockItemNotFoundException(sku)
        return repository.update(item.restock(quantity))
    }

    override fun get(sku: Sku): StockItem = repository.findBySku(sku) ?: throw StockItemNotFoundException(sku)
}
