package com.example.template.ordering.domain

// Ordering's product reference, shared by Order lines and Offers. Ordering checks only its form here; whether it
// is on sale is an Offer question, and inventory is never consulted.
@JvmInline
value class Sku(
    val value: String,
) {
    init {
        if (value.isBlank()) {
            throw InvalidSkuException("sku must not be blank")
        }
        if (value.length > MAX_LENGTH) {
            throw InvalidSkuException("sku must be at most $MAX_LENGTH characters")
        }
        if (value != value.trim()) {
            throw InvalidSkuException("sku must not start or end with whitespace")
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 64

        fun of(raw: String): Sku = Sku(raw.trim())
    }
}
