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
        validateFailureReason(reason)
        return CollectionRun(id, task, timeline.copy(finishedAt = at), CollectionRunStatus.FAILED, reason)
    }

    companion object {
        fun pending(
            id: UUID,
            task: TaskSnapshot,
            scheduledFor: Instant,
        ): CollectionRun = CollectionRun(id, task, RunTimeline(scheduledFor), CollectionRunStatus.PENDING)

        // Stored rows are validated like new transitions so that corrupted data fails at the boundary.
        fun restore(
            id: UUID,
            task: TaskSnapshot,
            timeline: RunTimeline,
            state: RunState,
        ): CollectionRun {
            val expectedStarted = state.status != CollectionRunStatus.PENDING
            val expectedFinished = state.status == CollectionRunStatus.SUCCEEDED || state.status == CollectionRunStatus.FAILED
            validateCollectionConfiguration(
                (timeline.startedAt != null) == expectedStarted && (timeline.finishedAt != null) == expectedFinished,
                "run timestamps do not match the ${state.status} status",
            )
            return CollectionRun(id, task, timeline, state.status, state.failureReason)
        }
    }
}
