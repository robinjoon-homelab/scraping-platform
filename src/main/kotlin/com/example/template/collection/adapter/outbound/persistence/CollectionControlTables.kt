package com.example.template.collection.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

internal object ManualRequestsTable : Table("collection_manual_requests") {
    val taskId = javaUUID("task_id")
    val requestKey = varchar("request_key", 120)
    val runId = javaUUID("run_id")
    override val primaryKey = PrimaryKey(taskId, requestKey)
}

internal object ExportSettingsTable : Table("collection_export_settings") {
    val taskId = javaUUID("task_id")
    val enabled = bool("enabled")
    override val primaryKey = PrimaryKey(taskId)
}

internal object ExportLedgerTable : Table("collection_export_ledger") {
    val sequence = long("publication_sequence")
    val taskId = javaUUID("task_id")
    val recordId = javaUUID("record_id").references(CollectedRecordsTable.id)
    override val primaryKey = PrimaryKey(sequence)
}

internal object ExportCounterTable : Table("collection_export_counter") {
    val id = integer("id")
    val position = long("published_position")
    override val primaryKey = PrimaryKey(id)
}
