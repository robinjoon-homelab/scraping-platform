package com.example.template.collection.application.service

import com.example.template.collection.domain.validateCollectionConfiguration

internal fun validateCollectionPage(
    offset: Int,
    limit: Int,
) {
    validateCollectionConfiguration(offset >= 0, "Offset must not be negative")
    validateCollectionConfiguration(limit in 1..100, "Limit must be between 1 and 100")
}
