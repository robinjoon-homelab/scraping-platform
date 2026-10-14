package com.example.template.collection.application.service

import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.application.port.input.CollectionTaskUseCase
import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.input.StaleTaskVersionException
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.TaskDefinition
import com.example.template.collection.domain.validateCollectionConfiguration
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Transactional(readOnly = true)
class CollectionTaskService(
    private val repository: CollectionTaskRepository,
    private val sources: SourceRepository,
    private val clock: Clock,
) : CollectionTaskUseCase {
    @Transactional
    override fun create(definition: TaskDefinition): CollectionTask {
        requireSource(definition.sourceId)
        return repository.insert(
            CollectionTask.create(
                id = UUID.randomUUID(),
                definition = definition,
                createdAt = Instant.now(clock),
            ),
        )
    }

    override fun get(id: UUID): CollectionTask = repository.findById(id) ?: throw CollectionTaskNotFoundException(id)

    override fun list(
        offset: Int,
        limit: Int,
    ): List<CollectionTask> {
        validateCollectionPage(offset, limit)
        return repository.findAll(offset, limit)
    }

    @Transactional
    override fun replace(
        id: UUID,
        expectedVersion: Long,
        definition: TaskDefinition,
    ): CollectionTask {
        val current = getAtVersion(id, expectedVersion)
        requireSource(definition.sourceId)
        val revised = current.revise(definition)
        return update(revised, expectedVersion)
    }

    @Transactional
    override fun setEnabled(
        id: UUID,
        expectedVersion: Long,
        enabled: Boolean,
    ): CollectionTask = update(getAtVersion(id, expectedVersion).changeEnabled(enabled), expectedVersion)

    private fun getAtVersion(
        id: UUID,
        expectedVersion: Long,
    ): CollectionTask {
        validateCollectionConfiguration(expectedVersion >= 0, "Expected version must not be negative")
        val current = get(id)
        if (current.version != expectedVersion) throw StaleTaskVersionException(id)
        return current
    }

    private fun requireSource(id: UUID) {
        if (sources.findById(id) == null) throw SourceNotFoundException(id)
    }

    private fun update(
        task: CollectionTask,
        expectedVersion: Long,
    ): CollectionTask = repository.update(task, expectedVersion) ?: throw StaleTaskVersionException(task.id)
}
