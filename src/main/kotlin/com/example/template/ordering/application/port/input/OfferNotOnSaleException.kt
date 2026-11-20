package com.example.template.ordering.application.port.input

import com.example.template.ordering.domain.Sku

// The SKUs of an order that have no Offer or only a discontinued one, sorted so the message is stable.
class OfferNotOnSaleException(
    skus: Set<Sku>,
) : RuntimeException("Not on sale: ${skus.map { it.value }.sorted().joinToString()}") {
    val skus: Set<Sku> = skus.toSet()
}
