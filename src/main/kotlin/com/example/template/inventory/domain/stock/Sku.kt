package com.example.template.inventory.domain.stock

// Inventory's own stock code. Ordering keeps a separate SKU type, so the two contexts share no domain types. The
// format excludes whitespace and slashes, which keeps every registered SKU usable as one path segment without
// trimming or encoding.
@JvmInline
value class Sku(
    val value: String,
) {
    init {
        if (!skuFormat.matches(value)) {
            throw InvalidStockException("sku must be 1 to 64 letters, digits, '.', '_' or '-' and start with a letter or digit")
        }
    }

    // Messages, logs, and paths use the plain code.
    override fun toString(): String = value

    companion object {
        private val skuFormat = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    }
}
