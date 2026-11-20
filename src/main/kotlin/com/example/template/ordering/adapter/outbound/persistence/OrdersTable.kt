package com.example.template.ordering.adapter.outbound.persistence

import com.example.template.ordering.domain.order.OrderStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object OrdersTable : Table("orders") {
    val id = javaUUID("id")
    val status = enumerationByName("status", 20, OrderStatus::class)
    val placedAt = timestampWithTimeZone("placed_at")

    override val primaryKey = PrimaryKey(id)
}
