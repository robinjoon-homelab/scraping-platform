package com.example.template.collection.application.port.output

import java.util.UUID

interface CollectionControlStore {
    fun lockTask(taskId: UUID)

    fun findRequest(
        taskId: UUID,
        requestKey: String,
    ): UUID?

    fun saveRequest(
        taskId: UUID,
        requestKey: String,
        runId: UUID,
    )
}
