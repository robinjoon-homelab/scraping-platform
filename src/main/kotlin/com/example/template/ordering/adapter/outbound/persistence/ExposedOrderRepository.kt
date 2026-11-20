package com.example.template.ordering.adapter.outbound.persistence

import com.example.template.ordering.application.port.output.OrderRepository
import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.order.Order
import com.example.template.ordering.domain.order.OrderId
import com.example.template.ordering.domain.order.OrderLine
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.ZoneOffset

class ExposedOrderRepository : OrderRepository {
    // The header and line inserts join the caller's transaction, so a failed line insert also discards the header.
    override fun add(order: Order): Order {
        OrdersTable.insert {
            it[id] = order.id.value
            it[status] = order.status
            it[placedAt] = order.placedAt.atOffset(ZoneOffset.UTC)
        }
        OrderLinesTable.batchInsert(order.lines.withIndex(), shouldReturnGeneratedValues = false) { (position, line) ->
            this[OrderLinesTable.orderId] = order.id.value
            this[OrderLinesTable.position] = position
            this[OrderLinesTable.sku] = line.sku.value
            this[OrderLinesTable.quantity] = line.quantity
        }
        return selectById(order.id).single().toOrder()
    }

    // The lines and placement time never change after placement, so only the status is written.
    override fun update(order: Order): Order {
        val updated = OrdersTable.update({ OrdersTable.id eq order.id.value }) { it[status] = order.status }
        check(updated == 1) { "Order to update does not exist: ${order.id}" }
        return selectById(order.id).single().toOrder()
    }

    override fun findById(id: OrderId): Order? = selectById(id).singleOrNull()?.toOrder()

    // Only the header row is locked. The lines are immutable, so reading them needs no lock.
    override fun findByIdForUpdate(id: OrderId): Order? = selectById(id).forUpdate().singleOrNull()?.toOrder()

    private fun selectById(id: OrderId): Query = OrdersTable.selectAll().where { OrdersTable.id eq id.value }

    private fun ResultRow.toOrder(): Order {
        val id = OrderId(this[OrdersTable.id])
        return Order.restore(id, linesOf(id), this[OrdersTable.status], this[OrdersTable.placedAt].toInstant())
    }

    private fun linesOf(id: OrderId): List<OrderLine> =
        OrderLinesTable
            .selectAll()
            .where { OrderLinesTable.orderId eq id.value }
            .orderBy(OrderLinesTable.position)
            .map { OrderLine(Sku(it[OrderLinesTable.sku]), it[OrderLinesTable.quantity]) }
}
