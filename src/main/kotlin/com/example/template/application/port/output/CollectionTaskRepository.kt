package com.example.template.application.port.output

import com.example.template.domain.CollectionTask
import java.util.UUID

interface CollectionTaskRepository {
    fun insert(task: CollectionTask): CollectionTask

    fun findById(id: UUID): CollectionTask?

    fun findAll(
        offset: Int,
        limit: Int,
    ): List<CollectionTask>

    fun update(
        task: CollectionTask,
        expectedVersion: Long,
    ): CollectionTask?
}
