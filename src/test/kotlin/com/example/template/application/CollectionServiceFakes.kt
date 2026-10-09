package com.example.template.application

import com.example.template.application.port.output.CollectionTaskRepository
import com.example.template.application.port.output.SourceRepository
import com.example.template.domain.CollectionTask
import com.example.template.domain.Source
import java.time.Instant
import java.util.UUID

internal class FakeSourceRepository(
    private val canonicalTime: Instant? = null,
) : SourceRepository {
    private val sources = linkedMapOf<UUID, Source>()
    private val inserts = mutableListOf<Source>()

    val insertedSources: List<Source> get() = inserts.toList()

    fun seed(vararg initialSources: Source) {
        initialSources.forEach { sources[it.id] = it }
    }

    override fun insert(source: Source): Source {
        val stored = source.copy(createdAt = canonicalTime ?: source.createdAt)
        sources[stored.id] = stored
        inserts += stored
        return stored
    }

    override fun findById(id: UUID): Source? = sources[id]

    override fun findAll(
        offset: Int,
        limit: Int,
    ): List<Source> = sources.values.drop(offset).take(limit)
}

internal class FakeCollectionTaskRepository(
    private val canonicalTime: Instant? = null,
) : CollectionTaskRepository {
    private val tasks = linkedMapOf<UUID, CollectionTask>()
    private val inserts = mutableListOf<CollectionTask>()
    private val updates = mutableListOf<CollectionTask>()
    private var updatesRejected = false

    val insertedTasks: List<CollectionTask> get() = inserts.toList()
    val updatedTasks: List<CollectionTask> get() = updates.toList()

    fun seed(vararg initialTasks: CollectionTask) {
        initialTasks.forEach { tasks[it.id] = it }
    }

    fun rejectUpdates() {
        updatesRejected = true
    }

    override fun insert(task: CollectionTask): CollectionTask {
        val stored = normalize(task)
        tasks[stored.id] = stored
        inserts += stored
        return stored
    }

    override fun findById(id: UUID): CollectionTask? = tasks[id]

    override fun findAll(
        offset: Int,
        limit: Int,
    ): List<CollectionTask> = tasks.values.drop(offset).take(limit)

    override fun update(
        task: CollectionTask,
        expectedVersion: Long,
    ): CollectionTask? {
        if (updatesRejected || tasks[task.id]?.version != expectedVersion) return null
        val stored = normalize(task)
        tasks[stored.id] = stored
        updates += stored
        return stored
    }

    private fun normalize(task: CollectionTask): CollectionTask {
        val schedule = task.definition.schedule
        return task.copy(
            createdAt = canonicalTime ?: task.createdAt,
            definition = task.definition.copy(schedule = schedule.copy(anchorAt = canonicalTime ?: schedule.anchorAt)),
        )
    }
}
