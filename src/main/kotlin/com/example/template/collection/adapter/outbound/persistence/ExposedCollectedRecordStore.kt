package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.application.port.output.CollectedRecordStore
import com.example.template.collection.domain.run.CollectedRecord
import org.jetbrains.exposed.v1.jdbc.insertIgnore
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

    private fun sha256(text: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

    private fun Instant.toStoredTime(): OffsetDateTime = truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
}
