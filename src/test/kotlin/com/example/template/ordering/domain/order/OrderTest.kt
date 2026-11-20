package com.example.template.ordering.domain.order

import com.example.template.ordering.domain.Sku
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class OrderTest {
    private val id = OrderId(UUID.fromString("5f0c6a43-3a5f-4d3e-9a51-1c2f3b4d5e6f"))
    private val keyboard = OrderLine(Sku("KB-001"), 3)
    private val mouse = OrderLine(Sku("MS-002"), 1)
    private val placedAt = Instant.parse("2026-10-10T00:00:00Z")
    private val placed = Order.place(id, listOf(keyboard, mouse), placedAt)

    @Test
    fun `placement starts placed with the lines in order and the time`() {
        assertThat(placed).isEqualTo(Order.restore(id, listOf(keyboard, mouse), OrderStatus.PLACED, placedAt))
        assertThat(placed.lines).containsExactly(keyboard, mouse)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 10])
    fun `line counts at the limits are allowed`(count: Int) {
        assertThat(Order.place(id, lines(count), placedAt).lines).hasSize(count)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 11])
    fun `line counts outside one to ten are rejected`(count: Int) {
        assertThatThrownBy { Order.place(id, lines(count), placedAt) }
            .isInstanceOf(InvalidOrderException::class.java)
            .hasMessage("an order must have between 1 and 10 lines")
    }

    @Test
    fun `a sku may appear on only one line even when the input differed only by whitespace`() {
        val repeated = listOf(keyboard, OrderLine(Sku.of(" KB-001 "), 2), mouse)

        assertThatThrownBy { Order.place(id, repeated, placedAt) }
            .isInstanceOf(InvalidOrderException::class.java)
            .hasMessage("each sku must appear on only one line: KB-001")
    }

    @Test
    fun `restoration checks the same rules as placement`() {
        assertThatThrownBy { Order.restore(id, listOf(keyboard, keyboard), OrderStatus.CANCELLED, placedAt) }
            .isInstanceOf(InvalidOrderException::class.java)
        assertThatThrownBy { Order.restore(id, emptyList(), OrderStatus.PLACED, placedAt) }
            .isInstanceOf(InvalidOrderException::class.java)
    }

    @Test
    fun `later changes to the caller's list do not reach the order`() {
        val source = mutableListOf(keyboard)
        val order = Order.place(id, source, placedAt)
        val restored = Order.restore(id, source, OrderStatus.PLACED, placedAt)

        source += mouse
        source.removeAt(0)

        assertThat(order.lines).containsExactly(keyboard)
        assertThat(restored.lines).containsExactly(keyboard)
    }

    @Test
    fun `the exposed lines reject writes even through a mutable view`() {
        assertThat(placed.lines).isUnmodifiable()
        assertThat(Order.restore(id, listOf(keyboard), OrderStatus.PLACED, placedAt).lines).isUnmodifiable()
    }

    private fun lines(count: Int): List<OrderLine> = (1..count).map { OrderLine(Sku("SKU-$it"), 1) }
}
