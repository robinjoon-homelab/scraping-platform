package com.example.template.todo.adapter.outbound.persistence

import com.example.template.support.persistence.MigratedH2Database
import com.example.template.todo.domain.todo.Todo
import com.example.template.todo.domain.todo.TodoId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class ExposedTodoRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedTodoRepository()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `add returns the same timestamp precision as a later read`() {
        val original = Todo.create(newId(), "Persist nanoseconds consistently", Instant.parse("2026-01-01T00:00:00.123456789Z"))

        val added = inTransaction { repository.add(original) }

        assertThat(inTransaction { repository.findById(original.id) }).isEqualTo(added)
        assertThat(added.id).isEqualTo(original.id)
        assertThat(added.title).isEqualTo(original.title)
        assertThat(added.completed).isFalse()
    }

    // An upsert would succeed here.
    @Test
    fun `adding an existing identifier fails instead of overwriting`() {
        val original = inTransaction { repository.add(todo("00000000-0000-0000-0000-000000000001")) }
        val duplicate = Todo.create(original.id, "Overwrite attempt", original.createdAt)

        assertThatThrownBy { inTransaction { repository.add(duplicate) } }.isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `update persists completion of the existing record`() {
        val original = inTransaction { repository.add(todo("00000000-0000-0000-0000-000000000001")) }

        val completed = inTransaction { repository.update(original.complete()) }

        assertThat(completed.completed).isTrue()
        assertThat(completed.createdAt).isEqualTo(original.createdAt)
        assertThat(inTransaction { repository.findById(original.id) }).isEqualTo(completed)
        assertThat(inTransaction { repository.findAll() }).containsExactly(completed)
    }

    @Test
    fun `updating a missing todo fails instead of inserting it`() {
        val missing = todo("00000000-0000-0000-0000-000000000001")

        assertThatIllegalStateException().isThrownBy { inTransaction { repository.update(missing) } }
        assertThat(inTransaction { repository.findAll() }).isEmpty()
    }

    @Test
    fun `read returns the stored todo or null`() {
        val stored = inTransaction { repository.add(todo("00000000-0000-0000-0000-000000000001")) }

        assertThat(inTransaction { repository.findById(stored.id) }).isEqualTo(stored)
        assertThat(inTransaction { repository.findById(newId()) }).isNull()
    }

    @Test
    fun `list uses identifier order when creation timestamps match`() {
        val first = todo("00000000-0000-0000-0000-000000000001")
        val second = todo("00000000-0000-0000-0000-000000000002")
        inTransaction {
            repository.add(second)
            repository.add(first)
        }

        assertThat(inTransaction { repository.findAll() }).containsExactly(first, second)
        assertThat(inTransaction { repository.findById(newId()) }).isNull()
    }

    private fun <T> inTransaction(block: () -> T): T = database.inTransaction(block)

    private fun newId() = TodoId(UUID.randomUUID())

    private fun todo(id: String) = Todo.create(TodoId(UUID.fromString(id)), "Persist the todo", Instant.parse("2026-01-01T00:00:00Z"))
}
