package com.example.template.domain

import java.time.Instant
import java.util.UUID

data class CollectionTask(
    val id: UUID,
    val definition: TaskDefinition,
    val enabled: Boolean = false,
    val version: Long = 0,
    val createdAt: Instant,
) {
    init {
        validateCollectionConfiguration(version >= 0, "version must not be negative")
        validateCollectionInstant(createdAt, "createdAt")
    }

    fun revise(definition: TaskDefinition): CollectionTask = copy(definition = definition, version = nextVersion())

    fun changeEnabled(enabled: Boolean): CollectionTask = copy(enabled = enabled, version = nextVersion())

    private fun nextVersion(): Long {
        validateCollectionConfiguration(version < Long.MAX_VALUE, "task version cannot be incremented")
        return version + 1
    }
}
