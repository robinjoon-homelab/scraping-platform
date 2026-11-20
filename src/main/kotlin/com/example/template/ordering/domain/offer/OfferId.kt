package com.example.template.ordering.domain.offer

import java.util.UUID

// The API identity of an Offer. The SKU stays the business key, but a UUID path keeps ordering free to accept
// SKUs that would not be safe as a URL path segment.
@JvmInline
value class OfferId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
