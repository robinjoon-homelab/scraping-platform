package com.example.template.collection.application.service

import com.example.template.collection.application.model.CollectedRecordSummary
import com.example.template.collection.application.model.StoredCollectedRecord
import com.example.template.collection.application.port.input.CollectedRecordNotFoundException
import com.example.template.collection.application.port.input.CollectionRunNotFoundException
import com.example.template.collection.application.port.input.CollectionRunQueryUseCase
import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.application.port.output.CollectedRecordStore
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.domain.run.CollectionRun
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional(readOnly = true)
class CollectionRunQueryService(
    private val tasks: CollectionTaskRepository,
    private val runs: CollectionRunRepository,
    private val records: CollectedRecordStore,
) : CollectionRunQueryUseCase {
    override fun listRuns(
        taskId: UUID,
        offset: Int,
        limit: Int,
    ): List<CollectionRun> {
        validateCollectionPage(offset, limit)
        if (tasks.findById(taskId) == null) throw CollectionTaskNotFoundException(taskId)
        return runs.findByTask(taskId, offset, limit)
    }

    override fun getRun(id: UUID): CollectionRun = runs.findById(id) ?: throw CollectionRunNotFoundException(id)

    override fun listRecords(
        runId: UUID,
        offset: Int,
        limit: Int,
    ): List<CollectedRecordSummary> {
        validateCollectionPage(offset, limit)
        if (runs.findById(runId) == null) throw CollectionRunNotFoundException(runId)
        return records.findFirstObservedBy(runId, offset, limit)
    }

    override fun getRecord(id: UUID): StoredCollectedRecord = records.findById(id) ?: throw CollectedRecordNotFoundException(id)
}
