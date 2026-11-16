package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.application.model.ExportStatus
import com.example.template.collection.application.port.output.CollectionExportStore
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class ExposedCollectionExportStore : CollectionExportStore {
    override fun configure(
        taskId: UUID,
        enabled: Boolean,
    ) {
        ExportSettingsTable.insertIgnore {
            it[ExportSettingsTable.taskId] = taskId
            it[ExportSettingsTable.enabled] = false
        }
        ExportSettingsTable.update({ ExportSettingsTable.taskId eq taskId }) { it[ExportSettingsTable.enabled] = enabled }
    }

    override fun status(taskId: UUID): ExportStatus {
        val enabled =
            ExportSettingsTable
                .selectAll()
                .where { ExportSettingsTable.taskId eq taskId }
                .singleOrNull()
                ?.get(ExportSettingsTable.enabled) ?: false
        val published = ExportLedgerTable.selectAll().where { ExportLedgerTable.taskId eq taskId }
        val through =
            published
                .orderBy(ExportLedgerTable.sequence to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.get(ExportLedgerTable.sequence) ?: 0L
        return ExportStatus(taskId, enabled, through, ExportLedgerTable.selectAll().where { ExportLedgerTable.taskId eq taskId }.count())
    }

    override fun publish(
        taskId: UUID,
        limit: Int,
    ) {
        val current =
            ExportCounterTable
                .selectAll()
                .where { ExportCounterTable.id eq 1 }
                .forUpdate()
                .single()[ExportCounterTable.position]
        val candidates =
            (CollectedRecordsTable innerJoin CollectionRunsTable)
                .leftJoin(ExportLedgerTable)
                .selectAll()
                .where {
                    (CollectionRunsTable.taskId eq taskId) and (CollectionRunsTable.status eq "SUCCEEDED") and
                        ExportLedgerTable.recordId.isNull()
                }.orderBy(CollectedRecordsTable.id to SortOrder.ASC)
                .limit(limit)
                .map { it[CollectedRecordsTable.id] }
        candidates.forEachIndexed { index, recordId ->
            ExportLedgerTable.insert {
                it[sequence] = current + index + 1
                it[ExportLedgerTable.taskId] = taskId
                it[ExportLedgerTable.recordId] = recordId
            }
        }
        ExportCounterTable.update({ ExportCounterTable.id eq 1 }) { it[position] = current + candidates.size }
    }

    override fun page(
        taskId: UUID,
        after: Long,
        limit: Int,
    ): List<Pair<Long, UUID>> =
        ExportLedgerTable
            .selectAll()
            .where { (ExportLedgerTable.taskId eq taskId) and (ExportLedgerTable.sequence greater after) }
            .orderBy(ExportLedgerTable.sequence to SortOrder.ASC)
            .limit(limit)
            .map { it[ExportLedgerTable.sequence] to it[ExportLedgerTable.recordId] }
}
