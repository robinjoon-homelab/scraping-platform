package com.example.template.inventory.application.port.input

import com.example.template.inventory.domain.stock.Sku
import com.example.template.inventory.domain.stock.StockItem

interface StockUseCase {
    // Fails with DuplicateSkuException when the SKU is already registered.
    fun register(
        sku: Sku,
        onHand: Int,
    ): StockItem

    fun restock(
        sku: Sku,
        quantity: Int,
    ): StockItem

    fun get(sku: Sku): StockItem
}
