package com.example.template.collection.domain.run

import com.example.template.collection.domain.task.TaskDefinition
import com.example.template.collection.domain.validateCollectionConfiguration
import java.util.UUID

data class TaskSnapshot(
    val taskId: UUID,
    val version: Long,
    val definition: TaskDefinition,
    val source: SourceSnapshot,
) {
    init {
        validateCollectionConfiguration(version >= 0, "task snapshot version must not be negative")
        validateCollectionConfiguration(definition.sourceId == source.id, "task snapshot source must match the task definition")
    }
}
