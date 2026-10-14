package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.application.port.output.CollectionRunSlotTakenException
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.CollectionRunStatus
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import com.example.template.support.persistence.MigratedH2Database
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

// Sources and tasks are seeded with SQL so that only the run adapter takes part.
class ExposedCollectionRunRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedCollectionRunRepository(JsonMapper.builder().build())
    private val scheduledAt = Instant.parse("2026-10-05T00:00:00.123456789Z")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `insert round trips the flattened snapshots and persisted timestamp precision`() {
        val original = CollectionRun.pending(UUID.randomUUID(), seedSnapshot(credentialRef = "api-key.v1"), scheduledAt)

        val saved = inTransaction { repository.insert(original) }

        assertThat(inTransaction { repository.findById(original.id) }).usingRecursiveComparison().isEqualTo(saved)
        assertThat(saved.task.taskId).isEqualTo(original.task.taskId)
        assertThat(saved.task.version).isEqualTo(original.task.version)
        assertThat(saved.task.definition.name).isEqualTo(original.task.definition.name)
        assertThat(saved.task.definition.target).isEqualTo(original.task.definition.target)
        assertThat(saved.task.source).isEqualTo(original.task.source)
        assertThat(saved.task.definition.target.parameters).containsExactlyInAnyOrderEntriesOf(original.task.definition.target.parameters)
        assertThat(saved.task.source.definition.credentialRef).isEqualTo("api-key.v1")
        assertThat(saved.status).isEqualTo(CollectionRunStatus.PENDING)
        assertThat(saved.scheduledFor).isEqualTo(Instant.parse("2026-10-05T00:00:00.123456Z"))
        assertThat(saved.task.definition.schedule.anchorAt).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456Z"))
        assertThat(saved.startedAt).isNull()
        assertThat(saved.finishedAt).isNull()
        assertThat(saved.failureReason).isNull()
        assertThat(inTransaction { repository.findById(UUID.randomUUID()) }).isNull()
    }

    @Test
    fun `update stores a successful transition and leaves the lease empty`() {
        val pending = inTransaction { repository.insert(CollectionRun.pending(UUID.randomUUID(), seedSnapshot(), scheduledAt)) }
        val started = scheduledAt.plusSeconds(1)
        val finished = scheduledAt.plusSeconds(2)

        val saved =
            requireNotNull(inTransaction { repository.update(pending.start(started).succeed(finished), CollectionRunStatus.PENDING) })

        assertThat(inTransaction { repository.findById(pending.id) }).usingRecursiveComparison().isEqualTo(saved)
        assertThat(saved.status).isEqualTo(CollectionRunStatus.SUCCEEDED)
        assertThat(saved.startedAt).isEqualTo(Instant.parse("2026-10-05T00:00:01.123456Z"))
        assertThat(saved.finishedAt).isEqualTo(Instant.parse("2026-10-05T00:00:02.123456Z"))
        assertThat(saved.task).isEqualTo(pending.task)
        assertThat(countRuns(leased = true)).isZero()
    }

    @Test
    fun `update stores a failure reason`() {
        val pending = inTransaction { repository.insert(CollectionRun.pending(UUID.randomUUID(), seedSnapshot(), scheduledAt)) }

        val saved =
            requireNotNull(
                inTransaction {
                    repository.update(pending.start(scheduledAt).fail(scheduledAt, "source unavailable"), CollectionRunStatus.PENDING)
                },
            )

        assertThat(saved.status).isEqualTo(CollectionRunStatus.FAILED)
        assertThat(saved.failureReason).isEqualTo("source unavailable")
        assertThat(inTransaction { repository.findById(pending.id) }?.failureReason).isEqualTo("source unavailable")
    }

    @Test
    fun `a stale object cannot overwrite a stored final state`() {
        val pending = inTransaction { repository.insert(CollectionRun.pending(UUID.randomUUID(), seedSnapshot(), scheduledAt)) }
        val running = pending.start(scheduledAt)
        inTransaction { repository.update(running, CollectionRunStatus.PENDING) }
        val succeeded =
            requireNotNull(inTransaction { repository.update(running.succeed(scheduledAt.plusSeconds(2)), CollectionRunStatus.RUNNING) })

        assertThat(
            inTransaction { repository.update(running.fail(scheduledAt.plusSeconds(3), "late"), CollectionRunStatus.RUNNING) },
        ).isNull()
        assertThat(inTransaction { repository.update(running, CollectionRunStatus.RUNNING) }).isNull()
        assertThat(
            inTransaction { repository.update(running.fail(scheduledAt.plusSeconds(3), "late"), CollectionRunStatus.SUCCEEDED) },
        ).isNull()

        val stored = requireNotNull(inTransaction { repository.findById(pending.id) })
        assertThat(stored).usingRecursiveComparison().isEqualTo(succeeded)
        assertThat(stored.status).isEqualTo(CollectionRunStatus.SUCCEEDED)
        assertThat(stored.finishedAt).isEqualTo(Instant.parse("2026-10-05T00:00:02.123456Z"))
        assertThat(stored.failureReason).isNull()
    }

    @Test
    fun `starting stores the running run with its lease only while the run is still pending`() {
        val pending = inTransaction { repository.insert(CollectionRun.pending(UUID.randomUUID(), seedSnapshot(), scheduledAt)) }
        val running = pending.start(scheduledAt.plusSeconds(1))
        val leaseUntil = scheduledAt.plusSeconds(301)

        val started = requireNotNull(inTransaction { repository.updateStarted(running, leaseUntil) })

        assertThat(started.status).isEqualTo(CollectionRunStatus.RUNNING)
        assertThat(started.startedAt).isEqualTo(Instant.parse("2026-10-05T00:00:01.123456Z"))
        assertThat(leaseOf(pending.id)).isEqualTo(Instant.parse("2026-10-05T00:05:01.123456Z"))
        assertThat(inTransaction { repository.updateStarted(running, leaseUntil.plusSeconds(60)) }).isNull()
        assertThat(leaseOf(pending.id)).isEqualTo(Instant.parse("2026-10-05T00:05:01.123456Z"))
        val missing = CollectionRun.pending(UUID.randomUUID(), seedSnapshot(), scheduledAt).start(scheduledAt)
        assertThat(inTransaction { repository.updateStarted(missing, leaseUntil) }).isNull()
    }

    @Test
    fun `closing a started run clears its lease`() {
        val pending = inTransaction { repository.insert(CollectionRun.pending(UUID.randomUUID(), seedSnapshot(), scheduledAt)) }
        val running = requireNotNull(inTransaction { repository.updateStarted(pending.start(scheduledAt), scheduledAt.plusSeconds(300)) })

        val failed =
            inTransaction { repository.update(running.fail(scheduledAt.plusSeconds(1), "lease expired"), CollectionRunStatus.RUNNING) }

        assertThat(failed?.status).isEqualTo(CollectionRunStatus.FAILED)
        assertThat(leaseOf(pending.id)).isNull()
    }

    @Test
    fun `pending runs are found once due, earliest scheduled first, up to the limit`() {
        val later = insertPending(scheduledAt.plusSeconds(120))
        val earliest = insertPending(scheduledAt)
        val middle = insertPending(scheduledAt.plusSeconds(60))
        insertPending(scheduledAt.plusSeconds(600))
        val started = insertPending(scheduledAt)
        inTransaction { repository.updateStarted(started.start(scheduledAt), scheduledAt.plusSeconds(300)) }
        val now = scheduledAt.plusSeconds(300)

        assertThat(inTransaction { repository.findPending(now, 2) }.map { it.id }).containsExactly(earliest.id, middle.id)
        assertThat(inTransaction { repository.findPending(now, 10) }.map { it.id }).containsExactly(earliest.id, middle.id, later.id)
    }

    @Test
    fun `expired leases are running runs whose lease ended before now or that have none`() {
        val leaseEnd = scheduledAt.plusSeconds(10)
        val expired = insertPending(scheduledAt)
        inTransaction { repository.updateStarted(expired.start(scheduledAt), leaseEnd) }
        val live = insertPending(scheduledAt)
        inTransaction { repository.updateStarted(live.start(scheduledAt), scheduledAt.plusSeconds(100)) }
        val unleased = insertPending(scheduledAt)
        inTransaction { repository.update(unleased.start(scheduledAt), CollectionRunStatus.PENDING) }
        insertPending(scheduledAt)

        assertThat(inTransaction { repository.findExpiredLeases(scheduledAt.plusSeconds(50), 10) }.map { it.id })
            .containsExactly(unleased.id, expired.id)
        assertThat(inTransaction { repository.findExpiredLeases(Instant.parse("2026-10-05T00:00:10.123456Z"), 10) }.map { it.id })
            .containsExactly(unleased.id)
        assertThat(inTransaction { repository.findExpiredLeases(scheduledAt.plusSeconds(50), 1) }.map { it.id })
            .containsExactly(unleased.id)
    }

    @Test
    fun `updating a missing run returns null`() {
        val missing = CollectionRun.pending(UUID.randomUUID(), seedSnapshot(), scheduledAt)

        assertThat(inTransaction { repository.update(missing.start(scheduledAt), CollectionRunStatus.PENDING) }).isNull()
        assertThat(inTransaction { repository.findById(missing.id) }).isNull()
    }

    @Test
    fun `a second run for the same task and scheduled time is rejected as a taken slot`() {
        val snapshot = seedSnapshot()
        val first = inTransaction { repository.insert(CollectionRun.pending(UUID.randomUUID(), snapshot, scheduledAt)) }

        assertThatThrownBy { inTransaction { repository.insert(CollectionRun.pending(UUID.randomUUID(), snapshot, scheduledAt)) } }
            .isInstanceOf(CollectionRunSlotTakenException::class.java)
            .hasMessageContaining(snapshot.taskId.toString())

        assertThat(inTransaction { repository.insert(CollectionRun.pending(UUID.randomUUID(), snapshot, scheduledAt.plusSeconds(60))) }.id)
            .isNotEqualTo(first.id)
        assertThat(countRuns()).isEqualTo(2)
    }

    @Test
    fun `slot existence matches the stored task and persisted scheduled time`() {
        val snapshot = seedSnapshot()
        inTransaction { repository.insert(CollectionRun.pending(UUID.randomUUID(), snapshot, scheduledAt)) }

        assertThat(inTransaction { repository.existsForSlot(snapshot.taskId, scheduledAt) }).isTrue()
        assertThat(inTransaction { repository.existsForSlot(snapshot.taskId, Instant.parse("2026-10-05T00:00:00.123456Z")) }).isTrue()
        assertThat(inTransaction { repository.existsForSlot(snapshot.taskId, scheduledAt.plusSeconds(60)) }).isFalse()
        assertThat(inTransaction { repository.existsForSlot(UUID.randomUUID(), scheduledAt) }).isFalse()
    }

    private fun countRuns(leased: Boolean = false): Long =
        inTransaction {
            CollectionRunsTable
                .selectAll()
                .count { !leased || it[CollectionRunsTable.leaseUntil] != null }
                .toLong()
        }

    private fun <T> inTransaction(block: () -> T): T = database.inTransaction(block)

    private fun insertPending(scheduledFor: Instant): CollectionRun =
        inTransaction { repository.insert(CollectionRun.pending(UUID.randomUUID(), seedSnapshot(), scheduledFor)) }

    private fun leaseOf(id: UUID): Instant? =
        inTransaction {
            CollectionRunsTable
                .selectAll()
                .where { CollectionRunsTable.id eq id }
                .single()[CollectionRunsTable.leaseUntil]
                ?.toInstant()
        }

    private fun seedSnapshot(credentialRef: String? = null): TaskSnapshot {
        val sourceId = UUID.randomUUID()
        val taskId = UUID.randomUUID()
        val credentialSql = credentialRef?.let { "'$it'" } ?: "NULL"
        database.execute(
            "INSERT INTO sources (id, display_name, kind, base_url, credential_ref, created_at) " +
                "VALUES ('$sourceId', '공개 데이터', 'HTTP_JSON', 'https://example.com', $credentialSql, " +
                "TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00')",
        )
        database.execute(
            "INSERT INTO collection_tasks " +
                "(id, display_name, source_id, resource, parameters, every_seconds, anchor_at, enabled, version, created_at) " +
                "VALUES ('$taskId', '주기적 지표 수집', '$sourceId', '/observations', '{}', 3600, " +
                "TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00', FALSE, 0, TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00')",
        )
        return TaskSnapshot(
            taskId = taskId,
            version = 0,
            definition =
                TaskDefinition(
                    name = "주기적 지표 수집",
                    sourceId = sourceId,
                    target = CollectionTarget("/observations", mapOf("검색어" to "금리·경제 📈", "quoted" to "\"line\nvalue\"")),
                    schedule = IntervalSchedule(3600, Instant.parse("2026-01-01T00:00:00.123456789Z")),
                ),
            source =
                SourceSnapshot(
                    id = sourceId,
                    definition = SourceDefinition("공개 데이터", SourceKind.HTTP_JSON, "https://example.com", credentialRef),
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
        )
    }
}
