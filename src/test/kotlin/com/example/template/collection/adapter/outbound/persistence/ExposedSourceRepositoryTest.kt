package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.support.persistence.MigratedH2Database
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class ExposedSourceRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedSourceRepository()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `insert returns all source fields at persisted timestamp precision`() {
        val original =
            source("00000000-0000-0000-0000-000000000001", Instant.parse("2026-01-01T00:00:00.123456789Z"))

        val saved = inTransaction { repository.insert(original) }

        assertThat(inTransaction { repository.findById(original.id) }).isEqualTo(saved)
        assertThat(saved.id).isEqualTo(original.id)
        assertThat(saved.definition).isEqualTo(original.definition)
        assertThat(saved.definition.credentialRef).isEqualTo("market-data-key")
        assertThat(saved.createdAt).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456Z"))
    }

    @Test
    fun `list uses timestamp and identifier ordering with offset and limit`() {
        val first = source("00000000-0000-0000-0000-000000000001")
        val second = source("00000000-0000-0000-0000-000000000002")
        val later =
            source("00000000-0000-0000-0000-000000000000", first.createdAt.plusSeconds(1))
        inTransaction {
            repository.insert(later)
            repository.insert(second)
            repository.insert(first)
        }

        assertThat(inTransaction { repository.findAll(0, 100) }).containsExactly(first, second, later)
        assertThat(inTransaction { repository.findAll(1, 1) }).containsExactly(second)
        assertThat(inTransaction { repository.findAll(3, 1) }).isEmpty()
        assertThat(inTransaction { repository.findById(UUID.randomUUID()) }).isNull()
    }

    @Test
    fun `credential reference can be absent`() {
        val original = source("00000000-0000-0000-0000-000000000001")
        val withoutCredential = Source.restore(original.id, original.definition.copy(credentialRef = null), original.createdAt)

        val saved = inTransaction { repository.insert(withoutCredential) }

        assertThat(saved).isEqualTo(withoutCredential)
        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(withoutCredential)
    }

    @Test
    fun `timestamp truncation preserves the latest supported year`() {
        val original =
            source("00000000-0000-0000-0000-000000000001", Instant.parse("9999-12-31T23:59:59.999999999Z"))

        val saved = inTransaction { repository.insert(original) }

        assertThat(saved.createdAt).isEqualTo(Instant.parse("9999-12-31T23:59:59.999999Z"))
        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
    }

    @Test
    fun `migration rejects unsupported source kind and blank names`() {
        val saved = inTransaction { repository.insert(source("00000000-0000-0000-0000-000000000001")) }

        assertSqlRejected("UPDATE sources SET kind = 'SCRIPT'")
        assertSqlRejected("UPDATE sources SET display_name = '   '")

        assertThat(inTransaction { repository.findById(saved.id) }).isEqualTo(saved)
    }

    private fun <T> inTransaction(block: () -> T): T = database.inTransaction(block)

    private fun assertSqlRejected(sql: String) {
        assertThatThrownBy { database.execute(sql) }.isInstanceOf(SQLException::class.java)
    }

    private fun source(
        id: String,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    ) = Source.create(
        id = UUID.fromString(id),
        definition =
            SourceDefinition(
                name = "시장 데이터",
                kind = SourceKind.HTTP_JSON,
                baseUrl = "https://example.com/data",
                credentialRef = "market-data-key",
            ),
        createdAt = createdAt,
    )
}
