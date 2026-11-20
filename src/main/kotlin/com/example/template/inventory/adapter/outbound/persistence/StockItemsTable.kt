package com.example.template.inventory.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table

internal object StockItemsTable : Table("stock_items") {
    val sku = varchar("sku", 64)
    val onHand = integer("on_hand")

    override val primaryKey = PrimaryKey(sku)
}
