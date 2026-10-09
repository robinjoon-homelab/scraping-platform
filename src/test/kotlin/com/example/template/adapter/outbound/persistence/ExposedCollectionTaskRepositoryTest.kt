package com.example.template.adapter.outbound.persistence

import com.example.template.application.port.output.CollectionTaskRepository
import com.example.template.application.port.output.SourceRepository
import com.example.template.domain.CollectionTarget
import com.example.template.domain.CollectionTask
import com.example.template.domain.IntervalSchedule
import com.example.template.domain.Source
import com.example.template.domain.SourceDefinition
import com.example.template.domain.SourceKind
import com.example.template.domain.TaskDefinition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import javax.sql.DataSource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.datasource.url=jdbc:h2:mem:task-repository-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
class ExposedCollectionTaskRepositoryTest {
    @Autowired
    private lateinit var repository: CollectionTaskRepository

    @Autowired
    private lateinit var sources: SourceRepository

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun clearDatabase() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM collection_tasks")
                statement.executeUpdate("DELETE FROM sources")
            }
        }
    }

    @Test
    fun `insert round trips Unicode JSON parameters and persisted timestamp precision`() {
        val source = requireNotNull(inTransaction { sources.insert(source()) })
        val original =
            task("00000000-0000-0000-0000-000000000001", source.id)
                .copy(createdAt = Instant.parse("2026-01-01T00:00:00.123456789Z"))

        val saved = requireNotNull(inTransaction { repository.insert(original) })

        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
        assertThat(saved.definition.target.parameters).containsExactlyInAnyOrderEntriesOf(original.definition.target.parameters)
        assertThat(saved.definition.target.resource).isEqualTo("/observations")
        assertThat(saved.definition.schedule.everySeconds).isEqualTo(3600)
        assertThat(saved.enabled).isFalse()
        assertThat(saved.version).isZero()
    }

    @Test
    fun `update replaces configuration and reads back new schedule precision`() {
        val firstSource = requireNotNull(inTransaction { sources.insert(source()) })
        val secondSource = requireNotNull(inTransaction { sources.insert(source()) })
        val original =
            requireNotNull(inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000001", firstSource.id)) })
        val replacement =
            TaskDefinition(
                name = "변경된 수집 작업",
                sourceId = secondSource.id,
                target = CollectionTarget("/releases", emptyMap()),
                schedule = IntervalSchedule(60, Instant.parse("2026-02-01T00:00:00.987654321Z")),
            )

        val saved = requireNotNull(inTransaction { repository.update(original.revise(replacement), original.version) })

        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
        assertThat(saved.definition.sourceId).isEqualTo(secondSource.id)
        assertThat(saved.definition.name).isEqualTo(replacement.name)
        assertThat(saved.definition.target).isEqualTo(replacement.target)
        assertThat(saved.definition.schedule.everySeconds).isEqualTo(60)
        assertThat(saved.version).isEqualTo(1)
        assertThat(saved.createdAt).isEqualTo(original.createdAt)
    }

    @Test
    fun `stale update leaves the stored task unchanged`() {
        val source = requireNotNull(inTransaction { sources.insert(source()) })
        val original =
            requireNotNull(inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000001", source.id)) })
        val enabled = requireNotNull(inTransaction { repository.update(original.changeEnabled(true), 0) })

        val stale = inTransaction { repository.update(original.revise(original.definition.copy(name = "outdated")), 0) }

        assertThat(stale).isNull()
        assertThat(inTransaction { repository.findById(original.id) }).isEqualTo(enabled)
        assertThat(enabled.enabled).isTrue()
        assertThat(enabled.version).isEqualTo(1)
    }

    @Test
    fun `only one competing update can consume the same version`() {
        val source = requireNotNull(inTransaction { sources.insert(source()) })
        val original =
            requireNotNull(inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000001", source.id)) })
        val candidates =
            listOf(
                original.revise(original.definition.copy(name = "first writer")),
                original.revise(original.definition.copy(name = "second writer")),
            )

        val results =
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                executor
                    .invokeAll(candidates.map { candidate -> Callable { inTransaction { repository.update(candidate, 0) } } })
                    .map { it.get() }
            }

        assertThat(results.filterNotNull()).hasSize(1)
        assertThat(results.filter { it == null }).hasSize(1)
        assertThat(inTransaction { repository.findById(original.id) }).isEqualTo(results.filterNotNull().single())
    }

    @Test
    fun `list uses timestamp and identifier ordering with offset and limit`() {
        val source = requireNotNull(inTransaction { sources.insert(source()) })
        val first =
            requireNotNull(inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000001", source.id)) })
        val second =
            requireNotNull(inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000002", source.id)) })
        val later =
            requireNotNull(
                inTransaction {
                    repository.insert(
                        task("00000000-0000-0000-0000-000000000000", source.id)
                            .copy(createdAt = first.createdAt.plusSeconds(1)),
                    )
                },
            )

        assertThat(inTransaction { repository.findAll(0, 100) }).containsExactly(first, second, later)
        assertThat(inTransaction { repository.findAll(1, 1) }).containsExactly(second)
        assertThat(inTransaction { repository.findAll(3, 1) }).isEmpty()
        assertThat(inTransaction { repository.findById(UUID.randomUUID()) }).isNull()
    }

    @Test
    fun `migration rejects missing sources invalid schedules and negative versions`() {
        val source = requireNotNull(inTransaction { sources.insert(source()) })
        val saved =
            requireNotNull(inTransaction { repository.insert(task("00000000-0000-0000-0000-000000000001", source.id)) })

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
        val source = requireNotNull(inTransaction { sources.insert(source()) })
        val missing = task("00000000-0000-0000-0000-000000000001", source.id)

        assertThat(inTransaction { repository.update(missing.changeEnabled(true), 0) }).isNull()
    }

    @Test
    fun `timestamp truncation preserves the latest supported anchor and creation time`() {
        val source = requireNotNull(inTransaction { sources.insert(source()) })
        val original = task("00000000-0000-0000-0000-000000000001", source.id)
        val latest = Instant.parse("9999-12-31T23:59:59.999999999Z")
        val definition = original.definition.copy(schedule = IntervalSchedule(60, latest))

        val saved = requireNotNull(inTransaction { repository.insert(original.copy(definition = definition, createdAt = latest)) })

        assertThat(saved.createdAt).isEqualTo(Instant.parse("9999-12-31T23:59:59.999999Z"))
        assertThat(saved.definition.schedule.anchorAt).isEqualTo(saved.createdAt)
        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
    }

    private fun <T> inTransaction(operation: () -> T): T? = TransactionTemplate(transactionManager).execute { operation() }

    private fun assertSqlRejected(sql: String) {
        assertThatThrownBy {
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.executeUpdate(sql) }
            }
        }.isInstanceOf(SQLException::class.java)
    }

    private fun source() =
        Source(
            id = UUID.randomUUID(),
            definition = SourceDefinition("공개 데이터", SourceKind.HTTP_JSON, "https://example.com"),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

    private fun task(
        id: String,
        sourceId: UUID,
    ) = CollectionTask(
        id = UUID.fromString(id),
        definition =
            TaskDefinition(
                name = "주기적 지표 수집",
                sourceId = sourceId,
                target = CollectionTarget("/observations", mapOf("검색어" to "금리·경제 📈", "quoted" to "\"line\nvalue\"")),
                schedule = IntervalSchedule(3600, Instant.parse("2026-01-01T00:00:00.123456789Z")),
            ),
        enabled = false,
        version = 0,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
