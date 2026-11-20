package com.example.template.ordering.adapter.outbound.persistence

import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.order.Order
import com.example.template.ordering.domain.order.OrderId
import com.example.template.ordering.domain.order.OrderLine
import com.example.template.ordering.domain.order.OrderStatus
import com.example.template.support.persistence.MigratedH2Database
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class OrderLinesMigrationTest {
    private val database = MigratedH2Database(target = "3")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `the adapter reads a converted order as a one line order`() {
        database.execute(
            "INSERT INTO orders (id, sku, quantity, status, placed_at) " +
                "VALUES ('$ORDER_ID', 'customer reference / preorder', 3, 'CANCELLED', TIMESTAMP WITH TIME ZONE '$PLACED_AT_SQL')",
        )
        database.migrate()
        val id = OrderId(UUID.fromString(ORDER_ID))

        assertThat(database.inTransaction { ExposedOrderRepository().findById(id) })
            .isEqualTo(
                Order.restore(
                    id,
                    listOf(OrderLine(Sku("customer reference / preorder"), 3)),
                    OrderStatus.CANCELLED,
                    Instant.parse(PLACED_AT),
                ),
            )
    }

    private companion object {
        const val ORDER_ID = "00000000-0000-0000-0000-000000000002"
        const val PLACED_AT = "2026-01-01T00:00:00Z"
        const val PLACED_AT_SQL = "2026-01-01 00:00:00+00:00"
    }
}
