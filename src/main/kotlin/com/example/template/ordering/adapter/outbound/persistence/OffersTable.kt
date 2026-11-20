package com.example.template.ordering.adapter.outbound.persistence

import com.example.template.ordering.domain.offer.OfferStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

internal object OffersTable : Table("offers") {
    val id = javaUUID("id")
    val sku = varchar("sku", 64)
    val status = enumerationByName("status", 20, OfferStatus::class)

    override val primaryKey = PrimaryKey(id)
}
