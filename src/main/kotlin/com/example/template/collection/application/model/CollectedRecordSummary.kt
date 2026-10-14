package com.example.template.collection.application.model

import com.example.template.collection.domain.run.RecordIdentity
import java.time.Instant
import java.util.UUID

// A listing row without the payload, which can be 1,000,000 characters long; payloadBytes is its UTF-8 size, which
// does not depend on how a database counts characters.
data class CollectedRecordSummary(
    val id: UUID,
    val identity: RecordIdentity,
    val schemaVersion: String,
    val collectedAt: Instant,
    val sourceUpdatedAt: Instant?,
    val payloadBytes: Int,
)
