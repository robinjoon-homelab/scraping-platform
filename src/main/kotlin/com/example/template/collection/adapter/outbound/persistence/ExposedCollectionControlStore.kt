package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.application.port.output.CollectionControlStore
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class ExposedCollectionControlStore : CollectionControlStore {
    override fun lockTask(taskId: UUID) {
        CollectionTasksTable
            .selectAll()
            .where { CollectionTasksTable.id eq taskId }
            .forUpdate()
            .singleOrNull()
    }

    override fun findRequest(
        taskId: UUID,
        requestKey: String,
    ): UUID? =
        ManualRequestsTable
            .selectAll()
            .where { (ManualRequestsTable.taskId eq taskId) and (ManualRequestsTable.requestKey eq requestKey) }
            .singleOrNull()
            ?.get(ManualRequestsTable.runId)

    override fun saveRequest(
        taskId: UUID,
        requestKey: String,
        runId: UUID,
    ) {
        ManualRequestsTable.insert {
            it[ManualRequestsTable.taskId] = taskId
            it[ManualRequestsTable.requestKey] = requestKey
            it[ManualRequestsTable.runId] = runId
        }
    }
}
