package com.example.template.application.port.input

import com.example.template.domain.CollectionTask
import com.example.template.domain.TaskDefinition
import java.util.UUID

interface CollectionTaskUseCase {
    fun create(definition: TaskDefinition): CollectionTask

    fun get(id: UUID): CollectionTask

    fun list(
        offset: Int,
        limit: Int,
    ): List<CollectionTask>

    fun replace(
        id: UUID,
        expectedVersion: Long,
        definition: TaskDefinition,
    ): CollectionTask

    fun setEnabled(
        id: UUID,
        expectedVersion: Long,
        enabled: Boolean,
    ): CollectionTask
}
