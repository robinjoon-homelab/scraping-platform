package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.TaskDefinition
import java.time.Instant
import java.util.UUID

data class CollectionTaskResponse(
    val id: UUID,
    val definition: TaskDefinition,
    val enabled: Boolean,
    val version: Long,
    val createdAt: Instant,
) {
    companion object {
        fun from(task: CollectionTask): CollectionTaskResponse =
            CollectionTaskResponse(task.id, task.definition, task.enabled, task.version, task.createdAt)
    }
}
