package com.example.template.ordering.application.service

import com.example.template.ordering.application.port.input.OfferNotOnSaleException
import com.example.template.ordering.application.port.input.OrderNotFoundException
import com.example.template.ordering.application.port.input.OrderUseCase
import com.example.template.ordering.application.port.output.OfferRepository
import com.example.template.ordering.application.port.output.OrderRepository
import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.offer.OfferStatus
import com.example.template.ordering.domain.order.Order
import com.example.template.ordering.domain.order.OrderId
import com.example.template.ordering.domain.order.OrderLine
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Transactional(readOnly = true)
class OrderService(
    private val repository: OrderRepository,
    private val offers: OfferRepository,
    private val clock: Clock,
) : OrderUseCase {
    // The order validates its own lines before any offer is read, and only the order is written. Being on sale is
    // checked against an unlocked read, not kept as an invariant: a discontinuation that commits after this read
    // does not undo the order.
    @Transactional
    override fun place(lines: List<OrderLine>): Order {
        val order = Order.place(OrderId(UUID.randomUUID()), lines, Instant.now(clock))
        requireOnSale(order.lines.map { it.sku }.toSet())
        return repository.add(order)
    }

    override fun get(id: OrderId): Order = repository.findById(id) ?: throw OrderNotFoundException(id)

    // The row lock serializes concurrent cancellations, so only the first one writes. The others read the
    // committed cancellation and return it unchanged.
    @Transactional
    override fun cancel(id: OrderId): Order {
        val order = repository.findByIdForUpdate(id) ?: throw OrderNotFoundException(id)
        val cancelled = order.cancel() ?: return order
        return repository.update(cancelled)
    }

    private fun requireOnSale(skus: Set<Sku>) {
        val onSale = offers.findBySkus(skus).filter { it.status == OfferStatus.ON_SALE }.map { it.sku }
        val notOnSale = skus - onSale.toSet()
        if (notOnSale.isNotEmpty()) {
            throw OfferNotOnSaleException(notOnSale)
        }
    }
}
