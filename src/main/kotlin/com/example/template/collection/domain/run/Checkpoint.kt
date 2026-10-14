package com.example.template.collection.domain.run

import com.example.template.collection.domain.validateCollectionConfiguration
import java.util.UUID

/** Only the connector interprets the value; changing task versions requires an explicit fresh start. */
data class Checkpoint(
    val taskId: UUID,
    val taskVersion: Long,
    val value: String,
) {
    init {
        validateCollectionConfiguration(taskVersion >= 0, "checkpoint task version must not be negative")
        validateCollectionConfiguration(
            value.isNotBlank() && value.length <= MAX_VALUE_LENGTH,
            "checkpoint value must be 1..16384 characters",
        )
    }

    internal fun requireMatches(task: TaskSnapshot) {
        validateCollectionConfiguration(
            taskId == task.taskId && taskVersion == task.version,
            "checkpoint must belong to the same task version",
        )
    }

    companion object {
        private const val MAX_VALUE_LENGTH = 16_384
    }
}
