package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import com.example.template.collection.domain.task.TaskRevision
import com.example.template.support.persistence.MigratedH2Database
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

// Sources are seeded with SQL so that only the task adapter takes part.
class ExposedCollectionTaskRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedCollectionTaskRepository(JsonMapper.builder().build())

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

        val saved = requireNotNull(inTransaction { repository.update(original.revise(replacement), original.version) })

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
        val enabled = requireNotNull(inTransaction { repository.update(original.changeEnabled(true), 0) })

        val stale = inTransaction { repository.update(original.revise(original.definition.copy(name = "outdated")), 0) }

        assertThat(stale).isNull()
        assertThat(inTransaction { repository.findById(original.id) }).isEqualTo(enabled)
        assertThat(enabled.enabled).isTrue()
        assertThat(enabled.version).isEqualTo(1)
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
    fun `migration rejects missing sources invalid schedules and negative versions`() {
        val sourceId = seedSource()
        val saved = inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000001", sourceId)) }

        assertSqlRejected("UPDATE collection_tasks SET source_id = '${UUID.randomUUID()}'")
        assertSqlRejected("UPDATE collection_tasks SET every_seconds = 59")
        assertSqlRejected("UPDATE collection_tasks SET every_seconds = 31536001")
        assertSqlRejected("UPDATE collection_tasks SET version = -1")
        assertSqlRejected("UPDATE collection_tasks SET resource = '//another-host.example'")
        assertSqlRejected("DELETE FROM sources")

        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
    }

    @Test
    fun `updating a missing task returns null`() {
        val missing = task("00000000-0000-0000-0000-000000000001", seedSource())

        assertThat(inTransaction { repository.update(missing.changeEnabled(true), 0) }).isNull()
        assertThat(inTransaction { repository.findById(missing.id) }).isNull()
    }

    @Test
    fun `timestamp truncation preserves the latest supported anchor and creation time`() {
        val original = task("00000000-0000-0000-0000-000000000001", seedSource())
        val latest = Instant.parse("9999-12-31T23:59:59.999999999Z")
        val definition = original.definition.copy(schedule = IntervalSchedule(60, latest))

        val saved =
            inTransaction {
                repository.insert(CollectionTask.restore(original.id, definition, TaskRevision(original.enabled, original.version), latest))
            }

        assertThat(saved.createdAt).isEqualTo(Instant.parse("9999-12-31T23:59:59.999999Z"))
        assertThat(saved.definition.schedule.anchorAt).isEqualTo(saved.createdAt)
        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
    }

    private fun <T> inTransaction(block: () -> T): T = database.inTransaction(block)

    private fun assertSqlRejected(sql: String) {
        assertThatThrownBy { database.execute(sql) }.isInstanceOf(SQLException::class.java)
    }

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
