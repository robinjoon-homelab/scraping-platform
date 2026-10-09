package com.example.template.adapter.outbound.persistence

import com.example.template.application.port.output.SourceRepository
import com.example.template.domain.Source
import com.example.template.domain.SourceDefinition
import com.example.template.domain.SourceKind
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
import javax.sql.DataSource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.datasource.url=jdbc:h2:mem:source-repository-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
class ExposedSourceRepositoryTest {
    @Autowired
    private lateinit var repository: SourceRepository

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
    fun `insert returns all source fields at persisted timestamp precision`() {
        val original =
            source("00000000-0000-0000-0000-000000000001")
                .copy(createdAt = Instant.parse("2026-01-01T00:00:00.123456789Z"))

        val saved = requireNotNull(inTransaction { repository.insert(original) })

        assertThat(inTransaction { repository.findById(original.id) }).isEqualTo(saved)
        assertThat(saved.id).isEqualTo(original.id)
        assertThat(saved.definition).isEqualTo(original.definition)
        assertThat(saved.definition.credentialRef).isEqualTo("market-data-key")
    }

    @Test
    fun `list uses timestamp and identifier ordering with offset and limit`() {
        val first = source("00000000-0000-0000-0000-000000000001")
        val second = source("00000000-0000-0000-0000-000000000002")
        val later =
            source("00000000-0000-0000-0000-000000000000")
                .copy(createdAt = first.createdAt.plusSeconds(1))
        inTransaction { repository.insert(later) }
        inTransaction { repository.insert(second) }
        inTransaction { repository.insert(first) }

        assertThat(inTransaction { repository.findAll(0, 100) }).containsExactly(first, second, later)
        assertThat(inTransaction { repository.findAll(1, 1) }).containsExactly(second)
        assertThat(inTransaction { repository.findAll(3, 1) }).isEmpty()
        assertThat(inTransaction { repository.findById(UUID.randomUUID()) }).isNull()
    }

    @Test
    fun `credential reference can be absent`() {
        val original = source("00000000-0000-0000-0000-000000000001")
        val withoutCredential = original.copy(definition = original.definition.copy(credentialRef = null))

        val saved = requireNotNull(inTransaction { repository.insert(withoutCredential) })

        assertThat(saved).isEqualTo(withoutCredential)
        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(withoutCredential)
    }

    @Test
    fun `timestamp truncation preserves the latest supported year`() {
        val original =
            source("00000000-0000-0000-0000-000000000001")
                .copy(createdAt = Instant.parse("9999-12-31T23:59:59.999999999Z"))

        val saved = requireNotNull(inTransaction { repository.insert(original) })

        assertThat(saved.createdAt).isEqualTo(Instant.parse("9999-12-31T23:59:59.999999Z"))
        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
    }

    @Test
    fun `migration rejects unsupported source kind and blank names`() {
        val saved = requireNotNull(inTransaction { repository.insert(source("00000000-0000-0000-0000-000000000001")) })

        assertSqlRejected("UPDATE sources SET kind = 'SCRIPT'")
        assertSqlRejected("UPDATE sources SET display_name = '   '")

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

    private fun source(id: String) =
        Source(
            id = UUID.fromString(id),
            definition =
                SourceDefinition(
                    name = "시장 데이터",
                    kind = SourceKind.HTTP_JSON,
                    baseUrl = "https://example.com/data",
                    credentialRef = "market-data-key",
                ),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
}
