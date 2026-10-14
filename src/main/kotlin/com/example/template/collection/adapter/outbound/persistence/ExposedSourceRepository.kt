package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class ExposedSourceRepository : SourceRepository {
    override fun insert(source: Source): Source {
        SourcesTable.insert {
            it[id] = source.id
            it[name] = source.definition.name
            it[kind] = source.definition.kind.name
            it[baseUrl] = source.definition.baseUrl
            it[credentialRef] = source.definition.credentialRef
            it[createdAt] = source.createdAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
        }
        return SourcesTable
            .selectAll()
            .where { SourcesTable.id eq source.id }
            .single()
            .toSource()
    }

    override fun findById(id: UUID): Source? =
        SourcesTable
            .selectAll()
            .where { SourcesTable.id eq id }
            .singleOrNull()
            ?.toSource()

    override fun findAll(
        offset: Int,
        limit: Int,
    ): List<Source> =
        SourcesTable
            .selectAll()
            .orderBy(SourcesTable.createdAt to SortOrder.ASC, SourcesTable.id to SortOrder.ASC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toSource() }

    private fun ResultRow.toSource(): Source =
        Source.restore(
            id = this[SourcesTable.id],
            definition =
                SourceDefinition(
                    name = this[SourcesTable.name],
                    kind = SourceKind.valueOf(this[SourcesTable.kind]),
                    baseUrl = this[SourcesTable.baseUrl],
                    credentialRef = this[SourcesTable.credentialRef],
                ),
            createdAt = this[SourcesTable.createdAt].toInstant(),
        )
}
