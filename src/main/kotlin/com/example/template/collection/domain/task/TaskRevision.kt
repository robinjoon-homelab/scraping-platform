package com.example.template.collection.domain.task

import com.example.template.collection.domain.validateCollectionConfiguration

/**
 * Every accepted management mutation advances the version, including a repeated activation value. Scheduler slot
 * advancement is not a management mutation and keeps the version.
 */
data class TaskRevision(
    val enabled: Boolean,
    val version: Long,
) {
    init {
        validateCollectionConfiguration(version >= 0, "version must not be negative")
    }

    internal fun next(enabled: Boolean = this.enabled): TaskRevision {
        validateCollectionConfiguration(version < Long.MAX_VALUE, "task version cannot be incremented")
        return TaskRevision(enabled, version + 1)
    }
}
