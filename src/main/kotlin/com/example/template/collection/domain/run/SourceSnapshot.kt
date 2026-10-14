package com.example.template.collection.domain.run

import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.validateCollectionInstant
import java.time.Instant
import java.util.UUID

data class SourceSnapshot(
    val id: UUID,
    val definition: SourceDefinition,
    val createdAt: Instant,
) {
    init {
        validateCollectionInstant(createdAt, "createdAt")
    }
}
