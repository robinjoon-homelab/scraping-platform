package com.example.template.ordering.application

import com.example.template.ordering.application.port.input.OfferNotOnSaleException
import com.example.template.ordering.application.port.input.OrderNotFoundException
import com.example.template.ordering.application.port.output.OfferRepository
import com.example.template.ordering.application.port.output.OrderRepository
import com.example.template.ordering.application.service.OrderService
import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.offer.Offer
import com.example.template.ordering.domain.offer.OfferId
import com.example.template.ordering.domain.offer.OfferStatus
import com.example.template.ordering.domain.order.InvalidOrderException
import com.example.template.ordering.domain.order.Order
import com.example.template.ordering.domain.order.OrderId
import com.example.template.ordering.domain.order.OrderLine
import com.example.template.ordering.domain.order.OrderStatus
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// Both repositories are strict mocks: a port call a test does not stub fails the test.
class OrderServiceTest {
    private val now = Instant.parse("2026-10-10T09:00:00Z")
    private val repository = mockk<OrderRepository>()
    private val offers = mockk<OfferRepository>()
    private val service = OrderService(repository, offers, Clock.fixed(now, ZoneOffset.UTC))
    private val keyboard = Offer.register(OfferId(UUID.randomUUID()), Sku("KB-001"))
    private val mouse = Offer.register(OfferId(UUID.randomUUID()), Sku("MS-002"))

    @Test
    fun `placement returns a placed order with the lines and the clock time`() {
        val lines = listOf(OrderLine(Sku("MS-002"), 1), OrderLine(Sku("KB-001"), 3))
        every { offers.findBySkus(setOf(Sku("KB-001"), Sku("MS-002"))) } returns listOf(keyboard, mouse)
        every { repository.add(any()) } answers { firstArg() }

        val placed = service.place(lines)

        assertThat(placed).isEqualTo(Order.restore(placed.id, lines, OrderStatus.PLACED, now))
    }

    @Test
    fun `an invalid order is rejected`() {
        val line = OrderLine(Sku("KB-001"), 1)

        assertThatThrownBy { service.place(emptyList()) }.isInstanceOf(InvalidOrderException::class.java)
        assertThatThrownBy { service.place(listOf(line, line)) }.isInstanceOf(InvalidOrderException::class.java)
        assertThatThrownBy { service.place(List(11) { OrderLine(Sku("SKU-$it"), 1) }) }
            .isInstanceOf(InvalidOrderException::class.java)
    }

    @Test
    fun `a sku without an offer is not on sale`() {
        every { offers.findBySkus(setOf(Sku("KB-001"), Sku("ZZ-404"))) } returns listOf(keyboard)

        assertThatThrownBy { service.place(listOf(OrderLine(Sku("KB-001"), 1), OrderLine(Sku("ZZ-404"), 1))) }
            .isInstanceOf(OfferNotOnSaleException::class.java)
            .hasMessage("Not on sale: ZZ-404")
    }

    @Test
    fun `a discontinued offer is not on sale`() {
        val discontinued = Offer.restore(mouse.id, mouse.sku, OfferStatus.DISCONTINUED)
        every { offers.findBySkus(setOf(Sku("MS-002"))) } returns listOf(discontinued)

        assertThatThrownBy { service.place(listOf(OrderLine(Sku("MS-002"), 1))) }
            .isInstanceOf(OfferNotOnSaleException::class.java)
            .hasMessage("Not on sale: MS-002")
    }

    @Test
    fun `every sku that is not on sale is reported together`() {
        val discontinued = Offer.restore(mouse.id, mouse.sku, OfferStatus.DISCONTINUED)
        every { offers.findBySkus(setOf(Sku("ZZ-404"), Sku("KB-001"), Sku("MS-002"))) } returns listOf(keyboard, discontinued)
        val lines = listOf(OrderLine(Sku("ZZ-404"), 1), OrderLine(Sku("KB-001"), 1), OrderLine(Sku("MS-002"), 1))

        assertThatThrownBy { service.place(lines) }
            .hasMessage("Not on sale: MS-002, ZZ-404")
            .isInstanceOfSatisfying(OfferNotOnSaleException::class.java) {
                assertThat(it.skus).containsExactlyInAnyOrder(Sku("MS-002"), Sku("ZZ-404"))
            }
    }

    @Test
    fun `each placement gets its own order identifier`() {
        every { offers.findBySkus(setOf(Sku("KB-001"))) } returns listOf(keyboard)
        every { repository.add(any()) } answers { firstArg() }

        val first = service.place(listOf(OrderLine(Sku("KB-001"), 1)))
        val second = service.place(listOf(OrderLine(Sku("KB-001"), 1)))

        assertThat(first.id).isNotEqualTo(second.id)
    }

    @Test
    fun `get returns an order and reports a missing one`() {
        val existing = order(OrderStatus.PLACED)
        val missingId = OrderId(UUID.randomUUID())
        every { repository.findById(existing.id) } returns existing
        every { repository.findById(missingId) } returns null

        assertThat(service.get(existing.id)).isEqualTo(existing)
        assertThatThrownBy { service.get(missingId) }
            .isInstanceOf(OrderNotFoundException::class.java)
            .hasMessage("Order not found: $missingId")
    }

    @Test
    fun `cancelling a placed order returns it cancelled`() {
        val placed = order(OrderStatus.PLACED)
        val expected = Order.restore(placed.id, placed.lines, OrderStatus.CANCELLED, placed.placedAt)
        every { repository.findByIdForUpdate(placed.id) } returns placed
        every { repository.update(any()) } answers { firstArg() }

        val cancelled = service.cancel(placed.id)

        assertThat(cancelled).isEqualTo(expected)
    }

    @Test
    fun `cancelling again returns the cancelled order unchanged`() {
        val cancelled = order(OrderStatus.CANCELLED)
        every { repository.findByIdForUpdate(cancelled.id) } returns cancelled

        assertThat(service.cancel(cancelled.id)).isEqualTo(cancelled)
    }

    @Test
    fun `cancelling a missing order reports it as not found`() {
        val missingId = OrderId(UUID.randomUUID())
        every { repository.findByIdForUpdate(missingId) } returns null

        assertThatThrownBy { service.cancel(missingId) }.isInstanceOf(OrderNotFoundException::class.java)
    }

    private fun order(status: OrderStatus) =
        Order.restore(OrderId(UUID.randomUUID()), listOf(OrderLine(Sku("KB-001"), 3)), status, now.minusSeconds(60))
}
