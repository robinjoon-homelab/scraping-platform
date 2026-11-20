package com.example.template.inventory.domain.stock

import com.example.template.domain.AggregateRoot

// One item per SKU owns its onHand quantity. The private constructor and copy keep every change behind register,
// restore, and restock, so no item holds a negative onHand.
@AggregateRoot
@ConsistentCopyVisibility
data class StockItem private constructor(
    val sku: Sku,
    val onHand: Int,
) {
    init {
        if (onHand < 0) {
            throw InvalidStockException("onHand must not be negative")
        }
    }

    fun restock(quantity: Int): StockItem {
        if (quantity <= 0) {
            throw InvalidStockException("restock quantity must be positive")
        }
        if (quantity > Int.MAX_VALUE - onHand) {
            throw InvalidStockException("onHand of SKU $sku must not exceed ${Int.MAX_VALUE} after restocking")
        }
        return copy(onHand = onHand + quantity)
    }

    companion object {
        fun register(
            sku: Sku,
            onHand: Int,
        ): StockItem = StockItem(sku, onHand)

        // Restoration validates the stored state without normalizing it.
        fun restore(
            sku: Sku,
            onHand: Int,
        ): StockItem = StockItem(sku, onHand)
    }
}
