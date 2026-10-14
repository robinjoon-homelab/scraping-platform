package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.application.port.output.CheckpointStore
import com.example.template.collection.domain.run.Checkpoint
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
