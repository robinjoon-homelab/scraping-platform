package com.example.template.collection.application.port.output

import com.example.template.collection.domain.task.CollectionTask
import java.time.Instant
import java.util.UUID

interface CollectionTaskRepository {
    fun insert(task: CollectionTask): CollectionTask

    fun findById(id: UUID): CollectionTask?

    fun findAll(
        offset: Int,
        limit: Int,
    ): List<CollectionTask>

    fun update(
        task: CollectionTask,
        expectedVersion: Long,
    ): CollectionTask?

    // Enabled tasks whose next run time is at or before now, earliest slot first.
    fun findDue(
        now: Instant,
        limit: Int,
    ): List<CollectionTask>

    // Enabled tasks without a next run time, which only rows stored before next run times existed can have.
    fun findUnscheduled(limit: Int): List<CollectionTask>

    // Stores only task.nextRunAt, and only while the stored version equals task.version and the stored next run time
    // equals expectedNextRunAt (both absent counts as equal). The version is not changed. Returns false when another
    // scheduler or an operator changed the task after it was read.
    fun advanceNextRun(
        task: CollectionTask,
        expectedNextRunAt: Instant?,
    ): Boolean
}
