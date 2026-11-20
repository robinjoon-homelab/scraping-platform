package com.example.template.todo.domain.todo

import com.example.template.domain.AggregateRoot
import java.time.Instant

// The private constructor and copy keep every state change behind create, restore, and complete.
@AggregateRoot
@ConsistentCopyVisibility
data class Todo private constructor(
    val id: TodoId,
    val title: String,
    val completed: Boolean,
    val createdAt: Instant,
) {
    init {
        if (title.isBlank()) {
            throw InvalidTodoTitleException("title must not be blank")
        }
        if (title.length > 200) {
            throw InvalidTodoTitleException("title must be at most 200 characters")
        }
    }

    fun complete(): Todo = if (completed) this else copy(completed = true)

    companion object {
        fun create(
            id: TodoId,
            title: String,
            createdAt: Instant,
        ): Todo = Todo(id, title.trim(), completed = false, createdAt)

        // Restoration validates stored state without normalizing it, so a read never rewrites data.
        fun restore(
            id: TodoId,
            title: String,
            completed: Boolean,
            createdAt: Instant,
        ): Todo = Todo(id, title, completed, createdAt)
    }
}
