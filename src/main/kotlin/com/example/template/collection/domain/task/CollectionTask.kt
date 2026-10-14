package com.example.template.collection.domain.task

import com.example.template.collection.domain.validateCollectionConfiguration
import com.example.template.domain.AggregateRoot
import java.time.Instant
import java.util.UUID

@AggregateRoot
@ConsistentCopyVisibility
data class CollectionTask private constructor(
    val id: UUID,
    val definition: TaskDefinition,
    private val revision: TaskRevision,
    private val timeline: TaskTimeline,
) {
    val enabled: Boolean get() = revision.enabled
    val version: Long get() = revision.version
    val createdAt: Instant get() = timeline.createdAt
    val nextRunAt: Instant? get() = timeline.nextRunAt

    init {
        validateCollectionConfiguration(enabled || nextRunAt == null, "a disabled task must not have a next run time")
    }

    fun revise(
        definition: TaskDefinition,
        now: Instant,
    ): CollectionTask {
        val normalized = definition.copy(name = definition.name.trim())
        return copy(definition = normalized, revision = revision.next(), timeline = scheduledTimeline(enabled, normalized, now))
    }

    fun changeEnabled(
        enabled: Boolean,
        now: Instant,
    ): CollectionTask = copy(revision = revision.next(enabled), timeline = scheduledTimeline(enabled, definition, now))

    // The due slot becomes the caller's run and the next slot follows now, so missed slots are not replayed. The version
    // stays unchanged so that scheduler progress never turns an operator's expectedVersion into a conflict.
    fun advanceSlot(now: Instant): CollectionTask {
        val due = nextRunAt
        check(enabled && due != null && due <= now) { "only an enabled task with a due slot can advance" }
        return copy(timeline = timeline.copy(nextRunAt = definition.schedule.nextAfter(now)))
    }

    // Enabled tasks stored before next run times existed have no slot yet; they start from the next slot after now.
    fun initializeNextRun(now: Instant): CollectionTask {
        check(enabled && nextRunAt == null) { "only an enabled task without a next run time can be initialized" }
        return copy(timeline = timeline.copy(nextRunAt = definition.schedule.nextAfter(now)))
    }

    private fun scheduledTimeline(
        enabled: Boolean,
        definition: TaskDefinition,
        now: Instant,
    ): TaskTimeline = timeline.copy(nextRunAt = if (enabled) definition.schedule.nextAfter(now) else null)

    companion object {
        fun create(
            id: UUID,
            definition: TaskDefinition,
            createdAt: Instant,
        ): CollectionTask =
            CollectionTask(id, definition.copy(name = definition.name.trim()), TaskRevision(false, 0), TaskTimeline(createdAt))

        fun restore(
            id: UUID,
            definition: TaskDefinition,
            revision: TaskRevision,
            timeline: TaskTimeline,
        ): CollectionTask = CollectionTask(id, definition, revision, timeline)
    }
}
