package com.example.template.todo.application.port.output

import com.example.template.todo.domain.todo.Todo
import com.example.template.todo.domain.todo.TodoId

// add and update return the stored state so callers see the database timestamp precision.
interface TodoRepository {
    // Fails instead of overwriting when the id already exists.
    fun add(todo: Todo): Todo

    // Fails instead of inserting when the todo no longer exists.
    fun update(todo: Todo): Todo

    fun findById(id: TodoId): Todo?

    fun findAll(): List<Todo>
}
