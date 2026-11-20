package com.example.template.ordering.application.port.input

import com.example.template.ordering.domain.order.Order
import com.example.template.ordering.domain.order.OrderId
import com.example.template.ordering.domain.order.OrderLine

interface OrderUseCase {
    // Accepts the order only when every SKU has an Offer on sale. Fails with OfferNotOnSaleException otherwise.
    // Inventory is never consulted.
    fun place(lines: List<OrderLine>): Order

    fun get(id: OrderId): Order

    // Repeating the request returns the cancelled order unchanged.
    fun cancel(id: OrderId): Order
}
