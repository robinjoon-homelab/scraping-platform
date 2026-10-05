package com.example.template.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class TodoTest {
    private val id = UUID.fromString("f786c559-88d0-4d86-a4d6-736b4688572b")
    private val createdAt = Instant.parse("2026-10-05T00:00:00Z")

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `blank titles are rejected`(title: String) {
        assertThatIllegalArgumentException().isThrownBy {
            Todo(id = id, title = title, createdAt = createdAt)
        }
    }

    @Test
    fun `a title with exactly 200 characters is allowed`() {
        val title = "a".repeat(200)

        val todo = Todo(id = id, title = title, createdAt = createdAt)

        assertThat(todo.title).isEqualTo(title)
        assertThat(todo.completed).isFalse()
    }

    @Test
    fun `a title exceeding 200 characters is rejected`() {
        assertThatIllegalArgumentException().isThrownBy {
            Todo(id = id, title = "a".repeat(201), createdAt = createdAt)
        }
    }

    @Test
    fun `completion preserves identity and leaves the original unchanged`() {
        val original = Todo(id = id, title = "Write the API docs", createdAt = createdAt)

        val completed = original.complete()

        assertThat(completed).isEqualTo(original.copy(completed = true))
        assertThat(original.completed).isFalse()
    }

    @Test
    fun `completing an already completed todo is idempotent`() {
        val completed = Todo(id = id, title = "Write the API docs", completed = true, createdAt = createdAt)

        assertThat(completed.complete()).isEqualTo(completed)
    }
}
