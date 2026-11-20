package com.example.template.ordering.application.port.input

import com.example.template.ordering.domain.offer.OfferId

class OfferNotFoundException(
    id: OfferId,
) : RuntimeException("Offer not found: $id")
