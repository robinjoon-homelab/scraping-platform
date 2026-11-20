package com.example.template.ordering.adapter.inbound.web

import com.example.template.ordering.application.port.input.OrderUseCase
import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.order.Order
import com.example.template.ordering.domain.order.OrderId
import com.example.template.ordering.domain.order.OrderLine
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderUseCase: OrderUseCase,
) {
    @PostMapping
    fun place(
        @RequestBody request: PlaceOrderRequest,
    ): ResponseEntity<OrderResponse> {
        val order = orderUseCase.place(request.lines.map { it.toOrderLine() })
        return ResponseEntity.created(URI.create("/api/orders/${order.id.value}")).body(OrderResponse.from(order))
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): OrderResponse = OrderResponse.from(orderUseCase.get(OrderId(id)))

    @PatchMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: UUID,
    ): OrderResponse = OrderResponse.from(orderUseCase.cancel(OrderId(id)))
}

// The order rules validate the lines, and Jackson rejects missing or null values including null list elements,
// so the request does not repeat them with bean validation.
data class PlaceOrderRequest(
    val lines: List<OrderLineRequest>,
)

data class OrderLineRequest(
    val sku: String,
    val quantity: Int,
) {
    fun toOrderLine(): OrderLine = OrderLine(Sku.of(sku), quantity)
}

data class OrderResponse(
    val id: UUID,
    val lines: List<OrderLineResponse>,
    val status: String,
    val placedAt: Instant,
) {
    companion object {
        fun from(order: Order): OrderResponse =
            OrderResponse(
                id = order.id.value,
                lines = order.lines.map { OrderLineResponse(sku = it.sku.value, quantity = it.quantity) },
                status = order.status.name,
                placedAt = order.placedAt,
            )
    }
}

data class OrderLineResponse(
    val sku: String,
    val quantity: Int,
)
