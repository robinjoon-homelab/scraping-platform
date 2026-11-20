package com.example.template.ordering.domain.offer

import com.example.template.ordering.domain.Sku

// A SKU names at most one Offer. The repository detects it through the stored unique key, which also catches a
// registration that another transaction committed first.
class DuplicateOfferException(
    sku: Sku,
    cause: Throwable,
) : RuntimeException("Offer already registered for SKU: $sku", cause)
