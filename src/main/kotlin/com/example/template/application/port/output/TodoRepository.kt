package com.example.template.application.port.output

import com.example.template.domain.Todo
import java.util.UUID

interface TodoRepository {
    fun save(todo: Todo): Todo

    fun findById(id: UUID): Todo?

    fun findAll(): List<Todo>
}
