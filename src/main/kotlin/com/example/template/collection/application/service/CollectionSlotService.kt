package com.example.template.collection.application.service

import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.task.CollectionTask
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// A separate bean so that each task gets its own transaction when CollectionSchedulingService calls it; a call from
// inside the same object would not start a new transaction.
@Transactional(readOnly = true)
class CollectionSlotService(
    private val tasks: CollectionTaskRepository,
    private val sources: SourceRepository,
    private val runs: CollectionRunRepository,
) {
    fun findUnscheduled(limit: Int): List<CollectionTask> = tasks.findUnscheduled(limit)

    fun findDue(
        now: Instant,
        limit: Int,
    ): List<CollectionTask> = tasks.findDue(now, limit)

    // Returns the task with its first slot, or null when another scheduler or an operator changed the task after it was read.
    @Transactional
    fun initialize(
        task: CollectionTask,
        now: Instant,
    ): CollectionTask? {
        val initialized = task.initializeNextRun(now)
        return initialized.takeIf { tasks.advanceNextRun(it, task.nextRunAt) }
    }

    // Claims the due slot read with the task and creates its pending run in the same transaction. Returns null without a
    // run when the slot was claimed elsewhere first or already has a run; the slot still advances in the latter case so
    // that the task does not retry a taken slot forever.
    @Transactional
    fun claim(
        task: CollectionTask,
        now: Instant,
    ): CollectionRun? {
        val source = sources.findById(task.definition.sourceId) ?: throw SourceNotFoundException(task.definition.sourceId)
        val advanced = task.advanceSlot(now)
        val scheduledFor = checkNotNull(task.nextRunAt)
        if (!tasks.advanceNextRun(advanced, scheduledFor) || runs.existsForSlot(task.id, scheduledFor)) return null
        return runs.insert(CollectionRun.pending(UUID.randomUUID(), snapshot(task, source), scheduledFor))
    }

    private fun snapshot(
        task: CollectionTask,
        source: Source,
    ): TaskSnapshot =
        TaskSnapshot(
            taskId = task.id,
            version = task.version,
            definition = task.definition,
            source = SourceSnapshot(source.id, source.definition, source.createdAt),
        )
}
