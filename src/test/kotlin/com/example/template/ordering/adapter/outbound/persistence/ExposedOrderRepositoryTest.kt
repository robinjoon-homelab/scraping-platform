package com.example.template.ordering.adapter.outbound.persistence

import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.order.Order
import com.example.template.ordering.domain.order.OrderId
import com.example.template.ordering.domain.order.OrderLine
import com.example.template.ordering.domain.order.OrderStatus
import com.example.template.support.persistence.MigratedH2Database
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class ExposedOrderRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedOrderRepository()

    private val placedAt = Instant.parse("2026-10-10T00:00:00.123456Z")

    // Deliberately not in SKU order, so only the stored position can restore it.
    private val lines = listOf(OrderLine(Sku("KB-002"), 3), OrderLine(Sku("KB-001"), 1), OrderLine(Sku("MS-001"), 100))

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `every status round trips with its lines in placement order and its timestamp`() {
        OrderStatus.entries.forEach { status ->
            val order = order(status)

            assertThat(inTransaction { repository.add(order) }).isEqualTo(order)
            assertThat(inTransaction { repository.findById(order.id) }).isEqualTo(order)
        }
    }

    @Test
    fun `an order with the most lines keeps every line in placement order`() {
        val mostLines = (Order.MAX_LINES downTo 1).map { OrderLine(Sku("SKU-$it"), it) }
        val order = Order.place(OrderId(UUID.randomUUID()), mostLines, placedAt)

        assertThat(inTransaction { repository.add(order) }.lines).containsExactlyElementsOf(mostLines)
        assertThat(inTransaction { repository.findById(order.id) }).isEqualTo(order)
    }

    @Test
    fun `orders sharing a sku keep their own lines`() {
        val first = inTransaction { repository.add(order(OrderStatus.PLACED)) }
        val secondLines = listOf(OrderLine(Sku("KB-001"), 7))
        val second = inTransaction { repository.add(Order.place(OrderId(UUID.randomUUID()), secondLines, placedAt)) }

        assertThat(inTransaction { repository.findById(first.id) }?.lines).isEqualTo(lines)
        assertThat(inTransaction { repository.findById(second.id) }?.lines).isEqualTo(secondLines)
    }

    @Test
    fun `update writes the status and keeps the placed lines`() {
        val placed = inTransaction { repository.add(order(OrderStatus.PLACED)) }
        val withOtherLines = Order.restore(placed.id, listOf(OrderLine(Sku("KB-999"), 99)), OrderStatus.CANCELLED, placedAt)
        val expected = Order.restore(placed.id, lines, OrderStatus.CANCELLED, placedAt)

        assertThat(inTransaction { repository.update(withOtherLines) }).isEqualTo(expected)
        assertThat(inTransaction { repository.findById(placed.id) }).isEqualTo(expected)
    }

    @Test
    fun `update of a missing order fails instead of inserting it`() {
        val missing = order(OrderStatus.CANCELLED)

        assertThatThrownBy { inTransaction { repository.update(missing) } }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Order to update does not exist: ${missing.id}")
        assertThat(inTransaction { repository.findById(missing.id) }).isNull()
    }

    @Test
    fun `findByIdForUpdate returns the stored order and null for a missing one`() {
        val stored = inTransaction { repository.add(order(OrderStatus.PLACED)) }

        assertThat(inTransaction { repository.findByIdForUpdate(stored.id) }).isEqualTo(stored)
        assertThat(inTransaction { repository.findByIdForUpdate(OrderId(UUID.randomUUID())) }).isNull()
    }

    @Test
    fun `adding an existing id fails instead of overwriting`() {
        val stored = inTransaction { repository.add(order(OrderStatus.PLACED)) }

        assertThatThrownBy { inTransaction { repository.add(Order.restore(stored.id, lines, OrderStatus.CANCELLED, placedAt)) } }
            .isInstanceOf(SQLException::class.java)
    }

    private fun <T> inTransaction(block: () -> T): T = database.inTransaction(block)

    private fun order(status: OrderStatus) = Order.restore(OrderId(UUID.randomUUID()), lines, status, placedAt)
}
