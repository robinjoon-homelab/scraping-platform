package com.example.template.collection.domain.run

import com.example.template.collection.domain.validateCollectionConfiguration

data class CollectedRecord(
    val identity: RecordIdentity,
    val schemaVersion: String,
    val payload: String,
    val provenance: RecordProvenance,
) {
    init {
        validateCollectionConfiguration(
            schemaVersion.isNotBlank() && schemaVersion.length <= MAX_SCHEMA_LENGTH,
            "record schema version must be 1..120 characters",
        )
        validateCollectionConfiguration(
            payload.isNotEmpty() && payload.length <= MAX_PAYLOAD_LENGTH,
            "record payload must be 1..1000000 characters",
        )
    }

    companion object {
        private const val MAX_SCHEMA_LENGTH = 120
        private const val MAX_PAYLOAD_LENGTH = 1_000_000
    }
}
