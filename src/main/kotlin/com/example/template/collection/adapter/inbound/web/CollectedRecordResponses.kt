package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.model.CollectedRecordSummary
import com.example.template.collection.application.model.StoredCollectedRecord
import com.example.template.collection.domain.run.RecordIdentity
import com.example.template.collection.domain.run.RecordProvenance
import java.time.Instant
import java.util.UUID

data class CollectedRecordSummaryResponse(
    val id: UUID,
    val identity: RecordIdentity,
    val schemaVersion: String,
    val collectedAt: Instant,
    val sourceUpdatedAt: Instant?,
    val payloadBytes: Int,
) {
    companion object {
        fun from(summary: CollectedRecordSummary): CollectedRecordSummaryResponse =
            CollectedRecordSummaryResponse(
                id = summary.id,
                identity = summary.identity,
                schemaVersion = summary.schemaVersion,
                collectedAt = summary.collectedAt,
                sourceUpdatedAt = summary.sourceUpdatedAt,
                payloadBytes = summary.payloadBytes,
            )
    }
}

data class CollectedRecordResponse(
    val id: UUID,
    val identity: RecordIdentity,
    val schemaVersion: String,
    val provenance: RecordProvenance,
    val payload: String,
) {
    companion object {
        fun from(stored: StoredCollectedRecord): CollectedRecordResponse =
            CollectedRecordResponse(
                id = stored.id,
                identity = stored.record.identity,
                schemaVersion = stored.record.schemaVersion,
                provenance = stored.record.provenance,
                payload = stored.record.payload,
            )
    }
}
