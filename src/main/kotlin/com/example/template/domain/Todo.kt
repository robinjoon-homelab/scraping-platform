package com.example.template.domain

import java.time.Instant
import java.util.UUID

data class Todo(
    val id: UUID,
    val title: String,
    val completed: Boolean = false,
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

    fun complete(): Todo = copy(completed = true)
}
