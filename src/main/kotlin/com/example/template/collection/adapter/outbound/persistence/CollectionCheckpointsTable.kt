package com.example.template.collection.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object CollectionCheckpointsTable : Table("collection_checkpoints") {
    val taskId = javaUUID("task_id").references(CollectionTasksTable.id)
    val taskVersion = long("task_version")
    val value = text("value")
    val runId = javaUUID("run_id").references(CollectionRunsTable.id)
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(taskId)
}
