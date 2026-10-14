package com.example.template.collection.domain.run

import com.example.template.collection.domain.validateCollectionConfiguration

/** The source key must distinguish resources within its source. The connector defines payload canonicalization. */
data class RecordIdentity(
    val sourceRecordKey: String,
    val revisionHash: String,
) {
    init {
        validateCollectionConfiguration(
            sourceRecordKey.isNotBlank() && sourceRecordKey.length <= MAX_KEY_LENGTH,
            "source record key must be 1..2048 characters",
        )
        validateCollectionConfiguration(
            sha256Pattern.matches(revisionHash),
            "revision hash must be 64 lowercase SHA-256 hexadecimal characters",
        )
    }

    companion object {
        private const val MAX_KEY_LENGTH = 2048
        private val sha256Pattern = Regex("[0-9a-f]{64}")
    }
}
