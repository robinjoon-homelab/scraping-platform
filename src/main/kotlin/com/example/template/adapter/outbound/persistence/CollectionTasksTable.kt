package com.example.template.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object CollectionTasksTable : Table("collection_tasks") {
    val id = javaUUID("id")
    val name = varchar("display_name", 120)
    val sourceId = javaUUID("source_id").references(SourcesTable.id)
    val resource = varchar("resource", 2048)
    val parameters = text("parameters")
    val everySeconds = long("every_seconds")
    val anchorAt = timestampWithTimeZone("anchor_at")
    val enabled = bool("enabled")
    val version = long("version")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
