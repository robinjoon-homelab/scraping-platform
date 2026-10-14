package com.example.template.collection.domain.source

import com.example.template.collection.domain.validateCollectionInstant
import com.example.template.domain.AggregateRoot
import java.time.Instant
import java.util.UUID

@AggregateRoot
@ConsistentCopyVisibility
data class Source private constructor(
    val id: UUID,
    val definition: SourceDefinition,
    val createdAt: Instant,
) {
    init {
        validateCollectionInstant(createdAt, "createdAt")
    }

    companion object {
        fun create(
            id: UUID,
            definition: SourceDefinition,
            createdAt: Instant,
        ): Source = Source(id, definition.copy(name = definition.name.trim()), createdAt)

        fun restore(
            id: UUID,
            definition: SourceDefinition,
            createdAt: Instant,
        ): Source = Source(id, definition, createdAt)
    }
}
