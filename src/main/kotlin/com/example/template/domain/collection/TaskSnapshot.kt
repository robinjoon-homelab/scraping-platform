package com.example.template.domain.collection

import com.example.template.domain.CollectionTask
import com.example.template.domain.Source
import com.example.template.domain.TaskDefinition
import com.example.template.domain.validateCollectionConfiguration
import java.util.UUID

data class TaskSnapshot(
    val taskId: UUID,
    val version: Long,
    val definition: TaskDefinition,
    val source: Source,
) {
    init {
        validateCollectionConfiguration(version >= 0, "task snapshot version must not be negative")
        validateCollectionConfiguration(definition.sourceId == source.id, "task snapshot source must match the task definition")
    }

    companion object {
        fun from(
            task: CollectionTask,
            source: Source,
        ): TaskSnapshot = TaskSnapshot(task.id, task.version, task.definition, source)
    }
}
