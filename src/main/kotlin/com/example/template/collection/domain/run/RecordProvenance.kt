package com.example.template.collection.domain.run

import com.example.template.collection.domain.validateCollectionInstant
import java.time.Instant
import java.util.UUID

/** Source timestamps retain historical dates and clock skew without imposing the collector's clock. */
data class RecordProvenance(
    val runId: UUID,
    val sourceId: UUID,
    val collectedAt: Instant,
    val sourceUpdatedAt: Instant? = null,
) {
    init {
        validateCollectionInstant(collectedAt, "collection timestamp")
    }
}
