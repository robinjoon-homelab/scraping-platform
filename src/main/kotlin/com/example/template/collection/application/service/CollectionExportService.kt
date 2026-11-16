package com.example.template.collection.application.service

import com.example.template.collection.application.model.ExportItem
import com.example.template.collection.application.model.ExportPage
import com.example.template.collection.application.model.ExportStatus
import com.example.template.collection.application.port.input.CollectionExportUseCase
import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.application.port.output.CollectedRecordStore
import com.example.template.collection.application.port.output.CollectionExportStore
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.domain.validateCollectionConfiguration
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional(readOnly = true)
class CollectionExportService(
    private val tasks: CollectionTaskRepository,
    private val exports: CollectionExportStore,
    private val records: CollectedRecordStore,
    private val runs: CollectionRunRepository,
) : CollectionExportUseCase {
    @Transactional
    override fun configure(
        taskId: UUID,
        enabled: Boolean,
    ): ExportStatus {
        requireTask(taskId)
        exports.configure(taskId, enabled)
        return exports.status(taskId)
    }

    override fun status(taskId: UUID): ExportStatus {
        requireTask(taskId)
        return exports.status(taskId)
    }

    @Transactional
    override fun pull(
        taskId: UUID,
        after: Long,
        limit: Int,
    ): ExportPage {
        requireTask(taskId)
        validateCollectionConfiguration(after >= 0 && limit in 1..100, "after must be nonnegative and limit must be 1..100")
        validateCollectionConfiguration(exports.status(taskId).enabled, "Export pull is disabled for this plan")
        exports.publish(taskId, limit)
        validateCollectionConfiguration(
            after <= exports.status(taskId).publishedThrough,
            "after is ahead of this plan publication position",
        )
        val items =
            exports.page(taskId, after, limit).map { (sequence, id) ->
                val record = requireNotNull(records.findById(id))
                val run = requireNotNull(runs.findById(record.record.provenance.runId))
                ExportItem(sequence, record, run.task)
            }
        return ExportPage(taskId, items, items.lastOrNull()?.sequence ?: after)
    }

    private fun requireTask(taskId: UUID) {
        if (tasks.findById(taskId) == null) throw CollectionTaskNotFoundException(taskId)
    }
}
