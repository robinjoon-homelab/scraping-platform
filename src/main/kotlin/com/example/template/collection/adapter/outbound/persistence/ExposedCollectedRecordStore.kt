package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.application.model.CollectedRecordSummary
import com.example.template.collection.application.model.StoredCollectedRecord
import com.example.template.collection.application.port.output.CollectedRecordStore
import com.example.template.collection.domain.run.CollectedRecord
import com.example.template.collection.domain.run.RecordIdentity
import com.example.template.collection.domain.run.RecordProvenance
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID

// The database decides what is a duplicate: INSERT ... ON CONFLICT DO NOTHING on UNIQUE(source_id, source_record_key_hash,
// revision_hash) never raises for a repeated version, even when another transaction inserts it at the same time. A
// read before the insert would let two workers both pass the check and one fail on the constraint.
class ExposedCollectedRecordStore : CollectedRecordStore {
    override fun saveNew(records: List<CollectedRecord>): Int =
        records.sumOf { record ->
            CollectedRecordsTable
                .insertIgnore {
                    it[id] = UUID.randomUUID()
                    it[sourceId] = record.provenance.sourceId
                    it[runId] = record.provenance.runId
                    it[sourceRecordKey] = record.identity.sourceRecordKey
                    it[sourceRecordKeyHash] = sha256(record.identity.sourceRecordKey)
                    it[revisionHash] = record.identity.revisionHash
                    it[schemaVersion] = record.schemaVersion
                    it[payload] = record.payload
                    it[collectedAt] = record.provenance.collectedAt.toStoredTime()
                    it[sourceUpdatedAt] = record.provenance.sourceUpdatedAt?.toStoredTime()
                }.insertedCount
        }

    // The payload column is not selected: its UTF-8 byte size (OCTET_LENGTH) is computed by the database so a page stays small.
    override fun findFirstObservedBy(
        runId: UUID,
        offset: Int,
        limit: Int,
    ): List<CollectedRecordSummary> {
        val payloadBytes = CustomFunction("OCTET_LENGTH", IntegerColumnType(), CollectedRecordsTable.payload)
        return CollectedRecordsTable
            .select(
                CollectedRecordsTable.id,
                CollectedRecordsTable.sourceRecordKey,
                CollectedRecordsTable.revisionHash,
                CollectedRecordsTable.schemaVersion,
                CollectedRecordsTable.collectedAt,
                CollectedRecordsTable.sourceUpdatedAt,
                payloadBytes,
            ).where { CollectedRecordsTable.runId eq runId }
            .orderBy(CollectedRecordsTable.collectedAt to SortOrder.ASC, CollectedRecordsTable.id to SortOrder.ASC)
            .limit(limit)
            .offset(offset.toLong())
            .map {
                CollectedRecordSummary(
                    id = it[CollectedRecordsTable.id],
                    identity = RecordIdentity(it[CollectedRecordsTable.sourceRecordKey], it[CollectedRecordsTable.revisionHash]),
                    schemaVersion = it[CollectedRecordsTable.schemaVersion],
                    collectedAt = it[CollectedRecordsTable.collectedAt].toInstant(),
                    sourceUpdatedAt = it[CollectedRecordsTable.sourceUpdatedAt]?.toInstant(),
                    payloadBytes = requireNotNull(it[payloadBytes]),
                )
            }
    }

    override fun findById(id: UUID): StoredCollectedRecord? =
        CollectedRecordsTable
            .selectAll()
            .where { CollectedRecordsTable.id eq id }
            .singleOrNull()
            ?.toStoredRecord()

    private fun ResultRow.toStoredRecord(): StoredCollectedRecord =
        StoredCollectedRecord(
            id = this[CollectedRecordsTable.id],
            record =
                CollectedRecord(
                    identity = RecordIdentity(this[CollectedRecordsTable.sourceRecordKey], this[CollectedRecordsTable.revisionHash]),
                    schemaVersion = this[CollectedRecordsTable.schemaVersion],
                    payload = this[CollectedRecordsTable.payload],
                    provenance =
                        RecordProvenance(
                            runId = this[CollectedRecordsTable.runId],
                            sourceId = this[CollectedRecordsTable.sourceId],
                            collectedAt = this[CollectedRecordsTable.collectedAt].toInstant(),
                            sourceUpdatedAt = this[CollectedRecordsTable.sourceUpdatedAt]?.toInstant(),
                        ),
                ),
        )

    private fun sha256(text: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

    private fun Instant.toStoredTime(): OffsetDateTime = truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
}
