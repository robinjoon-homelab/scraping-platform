package com.example.template.inventory.application.port.input

import com.example.template.inventory.domain.stock.Sku

class StockItemNotFoundException(
    sku: Sku,
) : RuntimeException("Stock item not found: $sku")
