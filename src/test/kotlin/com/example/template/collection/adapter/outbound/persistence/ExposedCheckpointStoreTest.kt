package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.domain.run.Checkpoint
import com.example.template.support.persistence.MigratedH2Database
import com.example.template.support.persistence.seedRun
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ExposedCheckpointStoreTest {
    private val database = MigratedH2Database()
    private val store = ExposedCheckpointStore()
    private val at = Instant.parse("2026-10-05T00:00:00Z")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `a task without a checkpoint has none`() {
        val run = database.seedRun()

        assertThat(inTransaction { store.find(run.taskId) }).isNull()
        assertThat(inTransaction { store.find(UUID.randomUUID()) }).isNull()
    }

    @Test
    fun `a saved checkpoint is found and a later one replaces it with its run and time`() {
        val run = database.seedRun()
        val laterRun = database.seedRun(sameTaskAs = run)

        inTransaction { store.save(Checkpoint(run.taskId, 0, "cursor-1"), run.runId, at) }
        assertThat(inTransaction { store.find(run.taskId) }).isEqualTo(Checkpoint(run.taskId, 0, "cursor-1"))

        inTransaction { store.save(Checkpoint(run.taskId, 0, "cursor-2"), laterRun.runId, at.plusSeconds(60)) }

        assertThat(inTransaction { store.find(run.taskId) }).isEqualTo(Checkpoint(run.taskId, 0, "cursor-2"))
        val row = inTransaction { CollectionCheckpointsTable.selectAll().single() }
        assertThat(row[CollectionCheckpointsTable.runId]).isEqualTo(laterRun.runId)
        assertThat(row[CollectionCheckpointsTable.updatedAt].toInstant()).isEqualTo(at.plusSeconds(60))
    }

    @Test
    fun `a checkpoint of a newer task version replaces the stored one but an older version never does`() {
        val run = database.seedRun()

        inTransaction { store.save(Checkpoint(run.taskId, 1, "version-1"), run.runId, at) }
        inTransaction { store.save(Checkpoint(run.taskId, 2, "version-2"), run.runId, at) }
        inTransaction { store.save(Checkpoint(run.taskId, 1, "stale"), run.runId, at) }

        assertThat(inTransaction { store.find(run.taskId) }).isEqualTo(Checkpoint(run.taskId, 2, "version-2"))
    }

    private fun <T> inTransaction(block: () -> T): T = database.inTransaction(block)
}
