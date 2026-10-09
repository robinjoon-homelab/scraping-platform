package com.example.template.domain.collection

import com.example.template.domain.validateCollectionConfiguration
import java.util.Collections

/** The next checkpoint is uncommitted; null leaves the previous checkpoint unchanged. */
class CollectionBatch(
    val request: CollectionRequest,
    records: List<CollectedRecord>,
    val nextCheckpoint: Checkpoint? = null,
) {
    val records: List<CollectedRecord> = Collections.unmodifiableList(ArrayList(records))

    init {
        validateCollectionConfiguration(this.records.size <= request.maxRecords, "batch must not exceed the requested record limit")
        validateCollectionConfiguration(
            this.records.sumOf { it.payload.length.toLong() } <= MAX_PAYLOAD_LENGTH,
            "batch payload must not exceed 4000000 characters",
        )
        this.records.forEach(::requireMatchingProvenance)
        nextCheckpoint?.requireMatches(request.run.task)
    }

    private fun requireMatchingProvenance(record: CollectedRecord) {
        val provenance = record.provenance
        validateCollectionConfiguration(provenance.runId == request.run.id, "record must belong to the requested run")
        validateCollectionConfiguration(provenance.sourceId == request.run.task.source.id, "record must belong to the requested source")
        val startedAt = checkNotNull(request.run.startedAt)
        validateCollectionConfiguration(provenance.collectedAt >= startedAt, "record must not be collected before the run starts")
    }

    companion object {
        private const val MAX_PAYLOAD_LENGTH = 4_000_000L
    }
}
