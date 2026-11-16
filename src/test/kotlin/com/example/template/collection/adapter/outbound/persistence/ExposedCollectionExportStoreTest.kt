package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.domain.run.CollectedRecord
import com.example.template.collection.domain.run.RecordIdentity
import com.example.template.collection.domain.run.RecordProvenance
import com.example.template.support.persistence.MigratedH2Database
import com.example.template.support.persistence.SeededRun
import com.example.template.support.persistence.seedRun
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ExposedCollectionExportStoreTest {
    private val database = MigratedH2Database()
    private val store = ExposedCollectionExportStore()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `historical successes publish once and continuation discovers later successes`() {
        val first = database.seedRun()
        save(first)
        succeed(first)
        database.inTransaction {
            assertThat(store.status(first.taskId).enabled).isFalse()
            store.configure(first.taskId, true)
            store.publish(first.taskId, 1)
        }
        val initial = database.inTransaction { store.page(first.taskId, 0, 1) }
        assertThat(initial).hasSize(1)
        val later = database.seedRun(sameTaskAs = first)
        save(later, "b")
        database.execute("UPDATE collected_records SET id='00000000-0000-0000-0000-000000000001' WHERE run_id='${later.runId}'")
        database.inTransaction { store.publish(first.taskId, 100) }
        assertThat(database.inTransaction { store.page(first.taskId, initial.last().first, 100) }).isEmpty()
        succeed(later)
        database.inTransaction { store.publish(first.taskId, 100) }
        val next = database.inTransaction { store.page(first.taskId, initial.last().first, 100) }
        assertThat(next).hasSize(1)
        assertThat(next.single().first).isGreaterThan(initial.single().first)
        database.inTransaction {
            store.publish(first.taskId, 100)
            assertThat(store.page(first.taskId, 0, 1)).isEqualTo(initial)
            assertThat(store.status(first.taskId).publishedCount).isEqualTo(2)
        }
    }

    @Test
    fun `publication scopes and configuration are persisted independently`() {
        val first = database.seedRun()
        val second = database.seedRun()
        save(first)
        save(second)
        succeed(first)
        succeed(second)
        database.inTransaction {
            store.configure(first.taskId, true)
            store.publish(first.taskId, 100)
            store.publish(second.taskId, 100)
            assertThat(store.status(first.taskId).enabled).isTrue()
            assertThat(store.status(second.taskId).enabled).isFalse()
            assertThat(store.page(first.taskId, 0, 100)).hasSize(1)
            assertThat(store.page(second.taskId, 0, 100)).hasSize(1)
            store.configure(first.taskId, false)
            assertThat(store.status(first.taskId).enabled).isFalse()
        }
    }

    @Test
    fun `version five records keep their identity and become available through initial pull`() {
        MigratedH2Database("5").use { legacy ->
            val run = legacy.seedRun()
            val recordId = java.util.UUID.randomUUID()
            val hash = "a".repeat(64)
            legacy.execute(
                "INSERT INTO collected_records (id, source_id, run_id, source_record_key, source_record_key_hash, " +
                    "revision_hash, schema_version, payload, collected_at) VALUES ('$recordId', '${run.sourceId}', '${run.runId}', " +
                    "'/legacy', '$hash', '$hash', 'http-json/1', '{}', TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00')",
            )
            legacy.execute("UPDATE collection_runs SET status='SUCCEEDED', finished_at=started_at WHERE id='${run.runId}'")
            legacy.migrate()
            legacy.inTransaction {
                store.configure(run.taskId, true)
                store.publish(run.taskId, 20)
                assertThat(store.page(run.taskId, 0, 20).single().second).isEqualTo(recordId)
            }
        }
    }

    private fun save(
        run: SeededRun,
        hash: String = "a",
    ) {
        val record =
            CollectedRecord(
                RecordIdentity("/items", hash.repeat(64)),
                "http-json-v1",
                "{}",
                RecordProvenance(run.runId, run.sourceId, Instant.parse("2026-01-01T00:00:00Z")),
            )
        database.inTransaction { ExposedCollectedRecordStore().saveNew(listOf(record)) }
    }

    private fun succeed(run: SeededRun) =
        database.execute(
            "UPDATE collection_runs SET status='SUCCEEDED', finished_at=started_at WHERE id='${run.runId}'",
        )
}
