package com.example.template.ordering.application.port.input

import com.example.template.ordering.domain.order.OrderId

class OrderNotFoundException(
    id: OrderId,
) : RuntimeException("Order not found: $id")
