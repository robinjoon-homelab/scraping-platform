package com.example.template.todo.application.port.input

import com.example.template.todo.domain.todo.Todo
import com.example.template.todo.domain.todo.TodoId

interface TodoUseCase {
    fun create(title: String): Todo

    fun get(id: TodoId): Todo

    fun list(): List<Todo>

    fun complete(id: TodoId): Todo
}
