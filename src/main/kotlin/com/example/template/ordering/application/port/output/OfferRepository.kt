package com.example.template.ordering.application.port.output

import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.offer.Offer
import com.example.template.ordering.domain.offer.OfferId

// add and update return the stored state read back after writing.
interface OfferRepository {
    // Fails with DuplicateOfferException instead of overwriting when the SKU already has an Offer, including one
    // that another transaction committed while this insert waited for it.
    fun add(offer: Offer): Offer

    // Writes the status of an offer loaded with findByIdForUpdate in the same transaction. Fails instead of
    // inserting when the offer does not exist.
    fun update(offer: Offer): Offer

    fun findById(id: OfferId): Offer?

    // Locks the offer row until the caller's writable transaction ends, so concurrent changes apply one after
    // another.
    fun findByIdForUpdate(id: OfferId): Offer?

    // Reads without locking. SKUs without an Offer are simply absent from the result.
    fun findBySkus(skus: Set<Sku>): List<Offer>
}
