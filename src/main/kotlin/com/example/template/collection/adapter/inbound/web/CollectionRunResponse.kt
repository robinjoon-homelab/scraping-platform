package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.CollectionRunStatus
import java.time.Instant
import java.util.UUID

// The worker lease is internal coordination state and is not part of the response.
data class CollectionRunResponse(
    val id: UUID,
    val task: RunTaskResponse,
    val status: CollectionRunStatus,
    val failureReason: String?,
    val times: RunTimesResponse,
) {
    companion object {
        fun from(run: CollectionRun): CollectionRunResponse =
            CollectionRunResponse(
                id = run.id,
                task = RunTaskResponse(run.task.taskId, run.task.version, run.task.source.id),
                status = run.status,
                failureReason = run.failureReason,
                times = RunTimesResponse(run.scheduledFor, run.startedAt, run.finishedAt),
            )
    }
}

data class RunTaskResponse(
    val id: UUID,
    val version: Long,
    val sourceId: UUID,
)

data class RunTimesResponse(
    val scheduledFor: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
)
