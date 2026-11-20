package com.example.template.ordering.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

internal object OrderLinesTable : Table("order_lines") {
    val orderId = javaUUID("order_id")

    // Not named "position": Exposed quotes that SQL keyword in lower case, which does not match H2's upper-case column.
    val position = integer("line_position")
    val sku = varchar("sku", 64)
    val quantity = integer("quantity")

    override val primaryKey = PrimaryKey(orderId, position)
}
