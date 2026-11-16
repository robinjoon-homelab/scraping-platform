package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.application.port.output.CheckpointStore
import com.example.template.collection.domain.run.Checkpoint
import com.example.template.collection.domain.run.TaskSnapshot
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class ExposedCheckpointStore : CheckpointStore {
    override fun find(taskId: UUID): Checkpoint? =
        CollectionCheckpointsTable
            .selectAll()
            .where { CollectionCheckpointsTable.taskId eq taskId }
            .singleOrNull()
            ?.let { Checkpoint(taskId, it[CollectionCheckpointsTable.taskVersion], it[CollectionCheckpointsTable.value]) }

    override fun isCompatible(task: TaskSnapshot): Boolean {
        val origin =
            (CollectionCheckpointsTable innerJoin CollectionRunsTable)
                .selectAll()
                .where { CollectionCheckpointsTable.taskId eq task.taskId }
                .singleOrNull() ?: return false
        if (origin[CollectionCheckpointsTable.taskVersion] < resumeFloor(task.taskId)) return false
        val mapper =
            tools.jackson.databind.json.JsonMapper
                .builder()
                .build()
        val parameters = mapper.readTree(origin[CollectionRunsTable.parameters])
        return origin[CollectionRunsTable.sourceId] == task.source.id &&
            origin[CollectionRunsTable.resource] == task.definition.target.resource &&
            parameters == mapper.valueToTree(task.definition.target.parameters)
    }

    // Update first, then insert only when no row took the update, and update once more when the insert was ignored. The
    // WHERE keeps a stored checkpoint of a newer task version from being replaced by a run that started before the task
    // changed. The second update covers another run inserting the first row between the first update and the insert:
    // without it a newer version's candidate would be dropped. MERGE, which H2 uses for an upsert, cannot take that
    // condition, so these statements are the same on both databases.
    override fun save(
        checkpoint: Checkpoint,
        runId: UUID,
        at: Instant,
    ) {
        if (checkpoint.taskVersion < resumeFloor(checkpoint.taskId)) return
        val stored = at.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)
        if (updateNotNewer(checkpoint, runId, stored) > 0) return
        val inserted =
            CollectionCheckpointsTable
                .insertIgnore {
                    it[taskId] = checkpoint.taskId
                    it[taskVersion] = checkpoint.taskVersion
                    it[value] = checkpoint.value
                    it[CollectionCheckpointsTable.runId] = runId
                    it[updatedAt] = stored
                }.insertedCount
        if (inserted == 0) updateNotNewer(checkpoint, runId, stored)
    }

    private fun resumeFloor(taskId: UUID): Long =
        CollectionTasksTable
            .selectAll()
            .where { CollectionTasksTable.id eq taskId }
            .single()[CollectionTasksTable.resumeFloor]

    private fun updateNotNewer(
        checkpoint: Checkpoint,
        runId: UUID,
        stored: OffsetDateTime,
    ): Int =
        CollectionCheckpointsTable.update({
            (CollectionCheckpointsTable.taskId eq checkpoint.taskId) and
                (CollectionCheckpointsTable.taskVersion lessEq checkpoint.taskVersion)
        }) {
            it[taskVersion] = checkpoint.taskVersion
            it[value] = checkpoint.value
            it[CollectionCheckpointsTable.runId] = runId
            it[updatedAt] = stored
        }
}
