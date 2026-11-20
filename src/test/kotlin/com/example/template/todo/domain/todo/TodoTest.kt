package com.example.template.todo.domain.todo

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class TodoTest {
    private val id = TodoId(UUID.fromString("f786c559-88d0-4d86-a4d6-736b4688572b"))
    private val createdAt = Instant.parse("2026-10-05T00:00:00Z")

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `blank titles are rejected`(title: String) {
        assertThatIllegalArgumentException().isThrownBy { Todo.create(id, title, createdAt) }
        assertThatIllegalArgumentException().isThrownBy { Todo.restore(id, title, completed = false, createdAt) }
    }

    @Test
    fun `a title with exactly 200 characters is allowed`() {
        val title = "a".repeat(200)

        val todo = Todo.create(id, title, createdAt)

        assertThat(todo.title).isEqualTo(title)
        assertThat(todo.completed).isFalse()
    }

    @Test
    fun `a title exceeding 200 characters is rejected`() {
        assertThatIllegalArgumentException().isThrownBy { Todo.create(id, "a".repeat(201), createdAt) }
        assertThatIllegalArgumentException().isThrownBy { Todo.restore(id, "a".repeat(201), completed = true, createdAt) }
    }

    @Test
    fun `creation normalizes the title and starts incomplete`() {
        val todo = Todo.create(id, "  Write the API docs  ", createdAt)

        assertThat(todo).isEqualTo(Todo.restore(id, "Write the API docs", completed = false, createdAt))
    }
}
