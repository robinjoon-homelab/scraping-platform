package com.example.template.inventory.application.port.output

import com.example.template.inventory.domain.stock.Sku
import com.example.template.inventory.domain.stock.StockItem

// add and update return the stored state read back after writing.
interface StockItemRepository {
    // Fails with DuplicateSkuException instead of overwriting when the SKU is already registered, including a
    // registration that another transaction committed while this insert waited for it.
    fun add(item: StockItem): StockItem

    // Writes onHand of an item loaded with findBySkuForUpdate in the same transaction. Fails instead of inserting
    // when the item does not exist.
    fun update(item: StockItem): StockItem

    fun findBySku(sku: Sku): StockItem?

    // Locks the item row until the caller's writable transaction ends, so concurrent changes to one item apply one
    // after another instead of overwriting each other.
    fun findBySkuForUpdate(sku: Sku): StockItem?
}
