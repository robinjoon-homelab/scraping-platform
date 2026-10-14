package com.example.template.collection.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object CollectedRecordsTable : Table("collected_records") {
    val id = javaUUID("id")
    val sourceId = javaUUID("source_id").references(SourcesTable.id)
    val runId = javaUUID("run_id").references(CollectionRunsTable.id)
    val sourceRecordKey = varchar("source_record_key", 2048)
    val sourceRecordKeyHash = char("source_record_key_hash", 64)
    val revisionHash = char("revision_hash", 64)
    val schemaVersion = varchar("schema_version", 120)
    val payload = text("payload")
    val collectedAt = timestampWithTimeZone("collected_at")
    val sourceUpdatedAt = timestampWithTimeZone("source_updated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
