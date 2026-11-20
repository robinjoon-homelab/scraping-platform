package com.example.template.todo.adapter.inbound.web.view

import com.example.template.todo.domain.todo.Todo
import java.util.UUID

data class TodoRowView(
    val id: UUID,
    val title: String,
    val completed: Boolean,
)

// Templates receive these views instead of domain objects so the page cannot depend on domain internals.
data class TodoListView(
    val rows: List<TodoRowView>,
    val remaining: Int,
) {
    companion object {
        fun from(todos: List<Todo>): TodoListView =
            TodoListView(
                rows = todos.map { TodoRowView(it.id.value, it.title, it.completed) },
                remaining = todos.count { !it.completed },
            )
    }
}
