package com.example.template.inventory.adapter.inbound.web

import com.example.template.inventory.application.port.input.StockUseCase
import com.example.template.inventory.domain.stock.Sku
import com.example.template.inventory.domain.stock.StockItem
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/inventory/items")
class StockController(
    private val stockUseCase: StockUseCase,
) {
    // A registered SKU contains only URL-safe characters, so it is used as the path segment as is.
    @PostMapping
    fun register(
        @RequestBody request: RegisterStockItemRequest,
    ): ResponseEntity<StockItemResponse> {
        val item = stockUseCase.register(Sku(request.sku), request.onHand)
        return ResponseEntity.created(URI.create("/api/inventory/items/${item.sku}")).body(StockItemResponse.from(item))
    }

    @GetMapping("/{sku}")
    fun get(
        @PathVariable sku: String,
    ): StockItemResponse = StockItemResponse.from(stockUseCase.get(Sku(sku)))

    // Each call adds to onHand, so restocking is a POST rather than an idempotent PUT.
    @PostMapping("/{sku}/restock")
    fun restock(
        @PathVariable sku: String,
        @RequestBody request: RestockRequest,
    ): StockItemResponse = StockItemResponse.from(stockUseCase.restock(Sku(sku), request.quantity))
}

// Value rules live in the domain. Missing or null fields fail JSON binding.
data class RegisterStockItemRequest(
    val sku: String,
    val onHand: Int,
)

data class RestockRequest(
    val quantity: Int,
)

data class StockItemResponse(
    val sku: String,
    val onHand: Int,
) {
    companion object {
        fun from(item: StockItem): StockItemResponse = StockItemResponse(sku = item.sku.value, onHand = item.onHand)
    }
}
