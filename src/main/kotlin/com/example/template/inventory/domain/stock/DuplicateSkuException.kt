package com.example.template.inventory.domain.stock

// A SKU names at most one StockItem. The repository detects it through the stored key, which also catches a
// registration that another transaction committed first.
class DuplicateSkuException(
    sku: Sku,
    cause: Throwable,
) : RuntimeException("SKU already registered: $sku", cause)
