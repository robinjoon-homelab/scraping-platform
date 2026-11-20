package com.example.template.ordering.domain.order

import java.util.UUID

@JvmInline
value class OrderId(
    val value: UUID,
) {
    // Messages and logs keep the plain UUID format of the public API.
    override fun toString(): String = value.toString()
}
