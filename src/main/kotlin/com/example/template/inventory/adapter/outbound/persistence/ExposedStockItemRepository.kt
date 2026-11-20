package com.example.template.inventory.adapter.outbound.persistence

import com.example.template.inventory.application.port.output.StockItemRepository
import com.example.template.inventory.domain.stock.DuplicateSkuException
import com.example.template.inventory.domain.stock.Sku
import com.example.template.inventory.domain.stock.StockItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ExposedStockItemRepository : StockItemRepository {
    // PostgreSQL makes an insert wait for a concurrent uncommitted insert of the same key and then reports a unique
    // violation, so concurrent registrations of one SKU also end as DuplicateSkuException. Other database errors,
    // including lock timeouts, stay technical failures.
    override fun add(item: StockItem): StockItem {
        try {
            StockItemsTable.insert {
                it[sku] = item.sku.value
                it[onHand] = item.onHand
            }
        } catch (exception: ExposedSQLException) {
            if (exception.sqlState == UNIQUE_VIOLATION) throw DuplicateSkuException(item.sku, exception)
            throw exception
        }
        return selectBySku(item.sku).single().toStockItem()
    }

    override fun update(item: StockItem): StockItem {
        val updated = StockItemsTable.update({ StockItemsTable.sku eq item.sku.value }) { it[onHand] = item.onHand }
        check(updated == 1) { "StockItem to update does not exist: ${item.sku}" }
        return selectBySku(item.sku).single().toStockItem()
    }

    override fun findBySku(sku: Sku): StockItem? = selectBySku(sku).singleOrNull()?.toStockItem()

    override fun findBySkuForUpdate(sku: Sku): StockItem? = selectBySku(sku).forUpdate().singleOrNull()?.toStockItem()

    private fun selectBySku(sku: Sku): Query = StockItemsTable.selectAll().where { StockItemsTable.sku eq sku.value }

    private fun ResultRow.toStockItem(): StockItem = StockItem.restore(Sku(this[StockItemsTable.sku]), this[StockItemsTable.onHand])

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
