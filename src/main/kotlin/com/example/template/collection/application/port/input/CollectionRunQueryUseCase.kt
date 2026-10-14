package com.example.template.collection.application.port.input

import com.example.template.collection.application.model.CollectedRecordSummary
import com.example.template.collection.application.model.StoredCollectedRecord
import com.example.template.collection.domain.run.CollectionRun
import java.util.UUID

interface CollectionRunQueryUseCase {
    // Newest scheduled time first. Throws CollectionTaskNotFoundException when the task does not exist.
    fun listRuns(
        taskId: UUID,
        offset: Int,
        limit: Int,
    ): List<CollectionRun>

    fun getRun(id: UUID): CollectionRun

    // Records the run observed first, in collection order. Throws CollectionRunNotFoundException when the run does not exist.
    fun listRecords(
        runId: UUID,
        offset: Int,
        limit: Int,
    ): List<CollectedRecordSummary>

    fun getRecord(id: UUID): StoredCollectedRecord
}
