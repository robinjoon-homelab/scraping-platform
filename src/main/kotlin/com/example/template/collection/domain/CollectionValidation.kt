package com.example.template.collection.domain

import java.time.Instant

private val earliestCollectionInstant = Instant.parse("1970-01-01T00:00:00Z")
private val latestCollectionInstant = Instant.parse("9999-12-31T23:59:59.999999999Z")

internal fun validateCollectionConfiguration(
    condition: Boolean,
    message: String,
) {
    if (!condition) {
        throw InvalidCollectionConfigurationException(message)
    }
}

internal fun validateCollectionInstant(
    value: Instant,
    field: String,
) {
    validateCollectionConfiguration(
        value in earliestCollectionInstant..latestCollectionInstant,
        "$field must be within years 1970 through 9999",
    )
}

internal fun validateCollectionName(name: String) {
    validateCollectionConfiguration(name.trim().length in 1..120, "name must contain 1 through 120 characters")
}
