package com.example.template.application

import com.example.template.application.port.input.TodoNotFoundException
import com.example.template.application.port.output.TodoRepository
import com.example.template.application.service.TodoService
import com.example.template.domain.Todo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TodoServiceTest {
    private val now = Instant.parse("2026-10-05T00:00:00Z")
    private val repository = FakeTodoRepository()
    private val service = TodoService(repository, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `creation trims the title and persists an incomplete todo with the clock time`() {
        val created = service.create("  Write the API docs  ")

        assertThat(created.title).isEqualTo("Write the API docs")
        assertThat(created.completed).isFalse()
        assertThat(created.createdAt).isEqualTo(now)
        assertThat(repository.savedTodos).containsExactly(created)
        assertThat(repository.findById(created.id)).isEqualTo(created)
    }

    @Test
    fun `invalid creation does not persist a todo`() {
        assertThatIllegalArgumentException().isThrownBy { service.create(" \t\n ") }

        assertThat(repository.savedTodos).isEmpty()
        assertThat(repository.findAll()).isEmpty()
    }

    @Test
    fun `get returns an existing todo without saving it`() {
        val existing = todo("Read the docs")
        repository.seed(existing)

        assertThat(service.get(existing.id)).isEqualTo(existing)
        assertThat(repository.savedTodos).isEmpty()
    }

    @Test
    fun `get reports a missing todo`() {
        val missingId = UUID.randomUUID()

        assertThatThrownBy { service.get(missingId) }
            .isInstanceOf(TodoNotFoundException::class.java)
            .hasMessage("Todo not found: $missingId")
        assertThat(repository.savedTodos).isEmpty()
    }

    @Test
    fun `completion persists the changed todo without mutating the original`() {
        val original = todo("Read the docs")
        repository.seed(original)

        val completed = service.complete(original.id)

        assertThat(completed).isEqualTo(original.copy(completed = true))
        assertThat(repository.savedTodos).containsExactly(completed)
        assertThat(repository.findById(original.id)).isEqualTo(completed)
        assertThat(original.completed).isFalse()
    }

    @Test
    fun `completion of a missing todo does not save anything`() {
        val missingId = UUID.randomUUID()

        assertThatThrownBy { service.complete(missingId) }
            .isInstanceOf(TodoNotFoundException::class.java)
            .hasMessage("Todo not found: $missingId")
        assertThat(repository.savedTodos).isEmpty()
    }

    @Test
    fun `list returns all stored todos without saving them`() {
        val first = todo("Read the docs")
        val second = todo("Try the API").complete()
        repository.seed(first, second)

        assertThat(service.list()).containsExactly(first, second)
        assertThat(repository.savedTodos).isEmpty()
    }

    @Test
    fun `list is empty when no todos exist`() {
        assertThat(service.list()).isEmpty()
    }

    private fun todo(title: String) = Todo(id = UUID.randomUUID(), title = title, createdAt = now)

    private class FakeTodoRepository : TodoRepository {
        private val todos = linkedMapOf<UUID, Todo>()
        val savedTodos = mutableListOf<Todo>()

        fun seed(vararg initialTodos: Todo) {
            initialTodos.forEach { todos[it.id] = it }
        }

        override fun save(todo: Todo): Todo {
            todos[todo.id] = todo
            savedTodos += todo
            return todo
        }

        override fun findById(id: UUID): Todo? = todos[id]

        override fun findAll(): List<Todo> = todos.values.toList()
    }
}
