package com.example.template.ordering.domain.order

import com.example.template.domain.AggregateRoot
import java.time.Instant
import java.util.Collections

// The rules across lines, such as distinct SKUs and the line count, need every line at once, so the lines live
// inside this root. The private constructor and copy keep every state change behind place, restore, and cancel.
@AggregateRoot
@ConsistentCopyVisibility
data class Order private constructor(
    val id: OrderId,
    val lines: List<OrderLine>,
    val status: OrderStatus,
    val placedAt: Instant,
) {
    init {
        if (lines.size !in MIN_LINES..MAX_LINES) {
            throw InvalidOrderException("an order must have between $MIN_LINES and $MAX_LINES lines")
        }
        val repeated =
            lines
                .groupingBy { it.sku }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        if (repeated.isNotEmpty()) {
            throw InvalidOrderException("each sku must appear on only one line: ${repeated.joinToString()}")
        }
    }

    // Returns null when the order is already cancelled, so callers write only a real change.
    fun cancel(): Order? =
        when (status) {
            OrderStatus.PLACED -> copy(status = OrderStatus.CANCELLED)
            OrderStatus.CANCELLED -> null
        }

    companion object {
        const val MIN_LINES = 1
        const val MAX_LINES = 10

        fun place(
            id: OrderId,
            lines: List<OrderLine>,
            placedAt: Instant,
        ): Order = Order(id, snapshot(lines), OrderStatus.PLACED, placedAt)

        // Restoration validates the stored lines with the same rules and only reassembles them.
        fun restore(
            id: OrderId,
            lines: List<OrderLine>,
            status: OrderStatus,
            placedAt: Instant,
        ): Order = Order(id, snapshot(lines), status, placedAt)

        // Copying detaches the order from the caller's list, and the unmodifiable view also rejects writes made
        // after casting the exposed list to a mutable one.
        private fun snapshot(lines: List<OrderLine>): List<OrderLine> = Collections.unmodifiableList(lines.toList())
    }
}
