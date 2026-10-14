package com.example.template.collection.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object CollectionRunsTable : Table("collection_runs") {
    val id = javaUUID("id")
    val taskId = javaUUID("task_id").references(CollectionTasksTable.id)
    val taskVersion = long("task_version")
    val taskName = varchar("task_display_name", 120)
    val resource = varchar("resource", 2048)
    val parameters = text("parameters")
    val everySeconds = long("every_seconds")
    val anchorAt = timestampWithTimeZone("anchor_at")
    val sourceId = javaUUID("source_id").references(SourcesTable.id)
    val sourceName = varchar("source_display_name", 120)
    val sourceKind = varchar("source_kind", 20)
    val sourceBaseUrl = varchar("source_base_url", 2048)
    val sourceCredentialRef = varchar("source_credential_ref", 120).nullable()
    val sourceCreatedAt = timestampWithTimeZone("source_created_at")
    val scheduledFor = timestampWithTimeZone("scheduled_for")
    val startedAt = timestampWithTimeZone("started_at").nullable()
    val finishedAt = timestampWithTimeZone("finished_at").nullable()
    val leaseUntil = timestampWithTimeZone("lease_until").nullable()
    val status = varchar("status", 20)
    val failureReason = varchar("failure_reason", 1000).nullable()

    override val primaryKey = PrimaryKey(id)
}
