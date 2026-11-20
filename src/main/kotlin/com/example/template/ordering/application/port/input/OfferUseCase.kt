package com.example.template.ordering.application.port.input

import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.offer.Offer
import com.example.template.ordering.domain.offer.OfferId

interface OfferUseCase {
    // Fails with DuplicateOfferException when the SKU already has an Offer.
    fun register(sku: Sku): Offer

    fun get(id: OfferId): Offer

    // Repeating the request returns the discontinued offer unchanged. Existing orders are not touched.
    fun discontinue(id: OfferId): Offer
}
