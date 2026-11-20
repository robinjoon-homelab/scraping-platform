package com.example.template.ordering.domain.offer

import com.example.template.domain.AggregateRoot
import com.example.template.ordering.domain.Sku

// Whether a SKU can be ordered. It is a separate aggregate from Order because merchandising changes it on its own
// schedule and many orders share it; orders keep only the SKU, so discontinuing never changes a placed order.
// The private constructor and copy keep every state change behind register, restore, and discontinue.
@AggregateRoot
@ConsistentCopyVisibility
data class Offer private constructor(
    val id: OfferId,
    val sku: Sku,
    val status: OfferStatus,
) {
    // Discontinuation is final. Returns null when the offer is already discontinued, so callers write only a
    // real change.
    fun discontinue(): Offer? =
        when (status) {
            OfferStatus.ON_SALE -> copy(status = OfferStatus.DISCONTINUED)
            OfferStatus.DISCONTINUED -> null
        }

    companion object {
        fun register(
            id: OfferId,
            sku: Sku,
        ): Offer = Offer(id, sku, OfferStatus.ON_SALE)

        fun restore(
            id: OfferId,
            sku: Sku,
            status: OfferStatus,
        ): Offer = Offer(id, sku, status)
    }
}
