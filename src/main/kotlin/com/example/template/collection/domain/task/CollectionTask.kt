package com.example.template.collection.domain.task

import com.example.template.collection.domain.validateCollectionInstant
import com.example.template.domain.AggregateRoot
import java.time.Instant
import java.util.UUID

@AggregateRoot
@ConsistentCopyVisibility
data class CollectionTask private constructor(
    val id: UUID,
    val definition: TaskDefinition,
    private val revision: TaskRevision,
    val createdAt: Instant,
) {
    val enabled: Boolean get() = revision.enabled
    val version: Long get() = revision.version

    init {
        validateCollectionInstant(createdAt, "createdAt")
    }

    fun revise(definition: TaskDefinition): CollectionTask =
        copy(definition = definition.copy(name = definition.name.trim()), revision = revision.next())

    fun changeEnabled(enabled: Boolean): CollectionTask = copy(revision = revision.next(enabled))

    companion object {
        fun create(
            id: UUID,
            definition: TaskDefinition,
            createdAt: Instant,
        ): CollectionTask = CollectionTask(id, definition.copy(name = definition.name.trim()), TaskRevision(false, 0), createdAt)

        fun restore(
            id: UUID,
            definition: TaskDefinition,
            revision: TaskRevision,
            createdAt: Instant,
        ): CollectionTask = CollectionTask(id, definition, revision, createdAt)
    }
}
