package com.example.template.collection.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object SourcesTable : Table("sources") {
    val id = javaUUID("id")
    val name = varchar("display_name", 120)
    val kind = varchar("kind", 20)
    val baseUrl = varchar("base_url", 2048)
    val credentialRef = varchar("credential_ref", 120).nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
