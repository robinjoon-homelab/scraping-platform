package com.example.template.adapter.outbound.persistence

import com.example.template.application.port.output.TodoRepository
import com.example.template.domain.Todo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.datasource.url=jdbc:h2:mem:repository-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
class ExposedTodoRepositoryTest {
    @Autowired
    private lateinit var repository: TodoRepository

    @Autowired
    private lateinit var dataSource: DataSource

    @BeforeEach
    fun clearDatabase() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate("DELETE FROM todos") }
        }
    }

    @Test
    fun `save returns the same timestamp precision as a later read`() {
        val original =
            Todo(
                id = UUID.randomUUID(),
                title = "Persist nanoseconds consistently",
                createdAt = Instant.parse("2026-01-01T00:00:00.123456789Z"),
            )

        val saved = repository.save(original)

        assertThat(repository.findById(original.id)).isEqualTo(saved)
        assertThat(saved.id).isEqualTo(original.id)
        assertThat(saved.title).isEqualTo(original.title)
        assertThat(saved.completed).isFalse()
    }

    @Test
    fun `saving completion updates the existing record`() {
        val original = repository.save(todo("00000000-0000-0000-0000-000000000001"))

        val completed = repository.save(original.complete())

        assertThat(completed.completed).isTrue()
        assertThat(repository.findById(original.id)).isEqualTo(completed)
        assertThat(repository.findAll()).containsExactly(completed)
    }

    @Test
    fun `list uses identifier order when creation timestamps match`() {
        val first = todo("00000000-0000-0000-0000-000000000001")
        val second = todo("00000000-0000-0000-0000-000000000002")
        repository.save(second)
        repository.save(first)

        assertThat(repository.findAll()).containsExactly(first, second)
        assertThat(repository.findById(UUID.randomUUID())).isNull()
    }

    private fun todo(id: String) =
        Todo(
            id = UUID.fromString(id),
            title = "Persist the todo",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
}
