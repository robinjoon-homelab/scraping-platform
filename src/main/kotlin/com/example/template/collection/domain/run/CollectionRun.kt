package com.example.template.collection.domain.run

import com.example.template.collection.domain.validateCollectionConfiguration
import com.example.template.domain.AggregateRoot
import java.time.Instant
import java.util.UUID

@AggregateRoot
class CollectionRun private constructor(
    val id: UUID,
    val task: TaskSnapshot,
    private val timeline: RunTimeline,
    val status: CollectionRunStatus,
    val failureReason: String? = null,
) {
    val scheduledFor: Instant get() = timeline.scheduledFor
    val startedAt: Instant? get() = timeline.startedAt
    val finishedAt: Instant? get() = timeline.finishedAt

    fun start(at: Instant): CollectionRun {
        check(status == CollectionRunStatus.PENDING) { "only a pending run can start" }
        return CollectionRun(id, task, timeline.copy(startedAt = at), CollectionRunStatus.RUNNING)
    }

    fun succeed(at: Instant): CollectionRun {
        check(status == CollectionRunStatus.RUNNING) { "only a running run can succeed" }
        return CollectionRun(id, task, timeline.copy(finishedAt = at), CollectionRunStatus.SUCCEEDED)
    }

    fun fail(
        at: Instant,
        reason: String,
    ): CollectionRun {
        check(status == CollectionRunStatus.RUNNING) { "only a running run can fail" }
        validateCollectionConfiguration(
            reason.isNotBlank() && reason.length <= MAX_FAILURE_LENGTH,
            "failure reason must be 1..1000 characters",
        )
        return CollectionRun(id, task, timeline.copy(finishedAt = at), CollectionRunStatus.FAILED, reason)
    }

    companion object {
        private const val MAX_FAILURE_LENGTH = 1000

        fun pending(
            id: UUID,
            task: TaskSnapshot,
            scheduledFor: Instant,
        ): CollectionRun = CollectionRun(id, task, RunTimeline(scheduledFor), CollectionRunStatus.PENDING)
    }
}
