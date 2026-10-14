package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import com.example.template.collection.domain.task.TaskRevision
import com.example.template.collection.domain.task.TaskTimeline
import com.example.template.support.persistence.MigratedH2Database
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// Sources are seeded with SQL so that only the task adapter takes part.
class ExposedCollectionTaskRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedCollectionTaskRepository(JsonMapper.builder().build())
    private val now = Instant.parse("2026-01-01T05:30:00.123456789Z")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `insert round trips Unicode JSON parameters and persisted timestamp precision`() {
        val sourceId = seedSource()
        val original =
            task("00000000-0000-0000-0000-000000000001", sourceId, Instant.parse("2026-01-01T00:00:00.123456789Z"))

        val saved = inTransaction { repository.insert(original) }

        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
        assertThat(saved.definition.target.parameters).containsExactlyInAnyOrderEntriesOf(original.definition.target.parameters)
        assertThat(saved.definition.target.resource).isEqualTo("/observations")
        assertThat(saved.definition.schedule.everySeconds).isEqualTo(3600)
        assertThat(saved.createdAt).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456Z"))
        assertThat(saved.enabled).isFalse()
        assertThat(saved.version).isZero()
    }

    @Test
    fun `update replaces configuration and reads back new schedule precision`() {
        val firstSourceId = seedSource()
        val secondSourceId = seedSource()
        val original = inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000001", firstSourceId)) }
        val replacement =
            TaskDefinition(
                name = "변경된 수집 작업",
                sourceId = secondSourceId,
                target = CollectionTarget("/releases", emptyMap()),
                schedule = IntervalSchedule(60, Instant.parse("2026-02-01T00:00:00.987654321Z")),
            )

        val saved = requireNotNull(inTransaction { repository.update(original.revise(replacement, now), original.version) })

        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
        assertThat(saved.definition.sourceId).isEqualTo(secondSourceId)
        assertThat(saved.definition.name).isEqualTo(replacement.name)
        assertThat(saved.definition.target).isEqualTo(replacement.target)
        assertThat(saved.definition.schedule.everySeconds).isEqualTo(60)
        assertThat(saved.definition.schedule.anchorAt).isEqualTo(Instant.parse("2026-02-01T00:00:00.987654Z"))
        assertThat(saved.version).isEqualTo(1)
        assertThat(saved.createdAt).isEqualTo(original.createdAt)
    }

    @Test
    fun `stale update leaves the stored task unchanged`() {
        val sourceId = seedSource()
        val original = inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000001", sourceId)) }
        val enabled = requireNotNull(inTransaction { repository.update(original.changeEnabled(true, now), 0) })

        val stale = inTransaction { repository.update(original.revise(original.definition.copy(name = "outdated"), now), 0) }

        assertThat(stale).isNull()
        assertThat(inTransaction { repository.findById(original.id) }).isEqualTo(enabled)
        assertThat(enabled.enabled).isTrue()
        assertThat(enabled.version).isEqualTo(1)
        assertThat(enabled.nextRunAt).isEqualTo(Instant.parse("2026-01-01T06:00:00.123456Z"))
    }

    @Test
    fun `list uses timestamp and identifier ordering with offset and limit`() {
        val sourceId = seedSource()
        val first = task("00000000-0000-0000-0000-000000000001", sourceId)
        val second = task("00000000-0000-0000-0000-000000000002", sourceId)
        val later = task("00000000-0000-0000-0000-000000000000", sourceId, first.createdAt.plusSeconds(1))
        val stored =
            inTransaction {
                repository.insert(later)
                repository.insert(second)
                repository.insert(first)
                repository.findAll(0, 100)
            }

        assertThat(stored.map { it.id }).containsExactly(first.id, second.id, later.id)
        assertThat(inTransaction { repository.findAll(1, 1) }).containsExactly(stored[1])
        assertThat(inTransaction { repository.findAll(3, 1) }).isEmpty()
        assertThat(inTransaction { repository.findById(UUID.randomUUID()) }).isNull()
    }

    @Test
    fun `migration rejects missing sources invalid schedules negative versions and next runs of disabled tasks`() {
        val sourceId = seedSource()
        val saved = inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000001", sourceId)) }

        assertSqlRejected("UPDATE collection_tasks SET source_id = '${UUID.randomUUID()}'")
        assertSqlRejected("UPDATE collection_tasks SET every_seconds = 59")
        assertSqlRejected("UPDATE collection_tasks SET every_seconds = 31536001")
        assertSqlRejected("UPDATE collection_tasks SET version = -1")
        assertSqlRejected("UPDATE collection_tasks SET resource = '//another-host.example'")
        assertSqlRejected("DELETE FROM sources")
        assertSqlRejected("UPDATE collection_tasks SET next_run_at = TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00'")

        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
    }

    @Test
    fun `updating a missing task returns null`() {
        val missing = task("00000000-0000-0000-0000-000000000001", seedSource())

        assertThat(inTransaction { repository.update(missing.changeEnabled(true, now), 0) }).isNull()
        assertThat(inTransaction { repository.findById(missing.id) }).isNull()
    }

    @Test
    fun `timestamp truncation preserves the latest supported anchor and creation time`() {
        val original = task("00000000-0000-0000-0000-000000000001", seedSource())
        val latest = Instant.parse("9999-12-31T23:59:59.999999999Z")
        val definition = original.definition.copy(schedule = IntervalSchedule(60, latest))

        val saved =
            inTransaction {
                repository.insert(
                    CollectionTask.restore(original.id, definition, TaskRevision(original.enabled, original.version), TaskTimeline(latest)),
                )
            }

        assertThat(saved.createdAt).isEqualTo(Instant.parse("9999-12-31T23:59:59.999999Z"))
        assertThat(saved.definition.schedule.anchorAt).isEqualTo(saved.createdAt)
        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
    }

    @Test
    fun `due tasks are enabled tasks whose next run is not after now in slot order`() {
        val sourceId = seedSource()
        val later = enabledTask("00000000-0000-0000-0000-000000000001", sourceId, now.minusSeconds(60))
        val earlier = enabledTask("00000000-0000-0000-0000-000000000002", sourceId, now.minusSeconds(120))
        val exactlyNow = enabledTask("00000000-0000-0000-0000-000000000003", sourceId, now)
        enabledTask("00000000-0000-0000-0000-000000000004", sourceId, now.plusSeconds(1))
        inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000005", sourceId)) }
        enabledTask("00000000-0000-0000-0000-000000000006", sourceId, null)

        val due = inTransaction { repository.findDue(now, 10) }

        assertThat(due.map { it.id }).containsExactly(earlier.id, later.id, exactlyNow.id)
        assertThat(due.first()).isEqualTo(earlier)
        assertThat(inTransaction { repository.findDue(now, 1) }.map { it.id }).containsExactly(earlier.id)
    }

    @Test
    fun `unscheduled tasks are enabled tasks without a next run`() {
        val sourceId = seedSource()
        val unscheduled = enabledTask("00000000-0000-0000-0000-000000000001", sourceId, null)
        enabledTask("00000000-0000-0000-0000-000000000002", sourceId, now)
        inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000003", sourceId)) }

        assertThat(inTransaction { repository.findUnscheduled(10) }).containsExactly(unscheduled)
    }

    @Test
    fun `advancing stores only the next run and keeps the version`() {
        val stored = enabledTask("00000000-0000-0000-0000-000000000001", seedSource(), now.minusSeconds(60))
        val advanced = stored.advanceSlot(now)

        assertThat(inTransaction { repository.advanceNextRun(advanced, stored.nextRunAt) }).isTrue()

        val reloaded = requireNotNull(inTransaction { repository.findById(stored.id) })
        assertThat(reloaded).isEqualTo(advanced)
        assertThat(reloaded.version).isEqualTo(stored.version)
        assertThat(reloaded.nextRunAt).isEqualTo(Instant.parse("2026-01-01T06:00:00.123456Z"))
    }

    @Test
    fun `advancing from a slot or version other than the stored one changes nothing`() {
        val stored = enabledTask("00000000-0000-0000-0000-000000000001", seedSource(), now.minusSeconds(60))
        val advanced = stored.advanceSlot(now)
        inTransaction { repository.advanceNextRun(advanced, stored.nextRunAt) }

        val competingScheduler = inTransaction { repository.advanceNextRun(stored.advanceSlot(now.plusSeconds(1)), stored.nextRunAt) }
        val operatorChanged = requireNotNull(inTransaction { repository.update(advanced.changeEnabled(true, now), advanced.version) })
        val staleVersion = inTransaction { repository.advanceNextRun(advanced.advanceSlot(now.plusSeconds(3600)), advanced.nextRunAt) }

        assertThat(competingScheduler).isFalse()
        assertThat(staleVersion).isFalse()
        assertThat(operatorChanged.nextRunAt).isEqualTo(advanced.nextRunAt)
        assertThat(inTransaction { repository.findById(stored.id) }).isEqualTo(operatorChanged)
    }

    @Test
    fun `initializing requires the stored next run to be absent`() {
        val sourceId = seedSource()
        val unscheduled = enabledTask("00000000-0000-0000-0000-000000000001", sourceId, null)
        val scheduled = enabledTask("00000000-0000-0000-0000-000000000002", sourceId, now)
        val initialized = unscheduled.initializeNextRun(now)

        assertThat(inTransaction { repository.advanceNextRun(initialized, null) }).isTrue()
        assertThat(inTransaction { repository.advanceNextRun(initialized, null) }).isFalse()
        assertThat(inTransaction { repository.advanceNextRun(scheduled.advanceSlot(now), null) }).isFalse()
        assertThat(inTransaction { repository.findById(unscheduled.id) }).isEqualTo(initialized)
        assertThat(inTransaction { repository.findById(scheduled.id) }).isEqualTo(scheduled)
    }

    private fun <T> inTransaction(block: () -> T): T = database.inTransaction(block)

    private fun assertSqlRejected(sql: String) {
        assertThatThrownBy { database.execute(sql) }.isInstanceOf(SQLException::class.java)
    }

    // Next run times are set with SQL so that due-time fixtures do not depend on the clock used by enabling.
    private fun enabledTask(
        id: String,
        sourceId: UUID,
        nextRunAt: Instant?,
    ): CollectionTask {
        val inserted = inTransaction { repository.insert(task(id, sourceId)) }
        val nextRunSql = nextRunAt?.let { "TIMESTAMP WITH TIME ZONE '${sqlTime(it)}'" } ?: "NULL"
        database.execute("UPDATE collection_tasks SET enabled = TRUE, version = 1, next_run_at = $nextRunSql WHERE id = '${inserted.id}'")
        return requireNotNull(inTransaction { repository.findById(inserted.id) })
    }

    private fun sqlTime(value: Instant): String =
        value
            .truncatedTo(ChronoUnit.MICROS)
            .toString()
            .replace('T', ' ')
            .replace("Z", "+00:00")

    private fun seedSource(): UUID {
        val id = UUID.randomUUID()
        database.execute(
            "INSERT INTO sources (id, display_name, kind, base_url, created_at) " +
                "VALUES ('$id', '공개 데이터', 'HTTP_JSON', 'https://example.com', TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00')",
        )
        return id
    }

    private fun task(
        id: String,
        sourceId: UUID,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    ) = CollectionTask.create(
        id = UUID.fromString(id),
        definition =
            TaskDefinition(
                name = "주기적 지표 수집",
                sourceId = sourceId,
                target = CollectionTarget("/observations", mapOf("검색어" to "금리·경제 📈", "quoted" to "\"line\nvalue\"")),
                schedule = IntervalSchedule(3600, Instant.parse("2026-01-01T00:00:00.123456789Z")),
            ),
        createdAt = createdAt,
    )
}
