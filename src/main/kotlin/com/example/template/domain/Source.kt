package com.example.template.domain

import java.time.Instant
import java.util.UUID

data class Source(
    val id: UUID,
    val definition: SourceDefinition,
    val createdAt: Instant,
) {
    init {
        validateCollectionInstant(createdAt, "createdAt")
    }
}
