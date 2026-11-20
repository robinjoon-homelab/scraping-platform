package com.example.template.ordering.domain.order

import com.example.template.ordering.domain.Sku

// A child value of Order. It has no identity outside its order and is never stored or changed on its own; the
// SKU names an Offer by its business key instead of holding the Offer.
data class OrderLine(
    val sku: Sku,
    val quantity: Int,
) {
    init {
        if (quantity !in MIN_QUANTITY..MAX_QUANTITY) {
            throw InvalidOrderException("quantity must be between $MIN_QUANTITY and $MAX_QUANTITY")
        }
    }

    companion object {
        const val MIN_QUANTITY = 1
        const val MAX_QUANTITY = 100
    }
}
