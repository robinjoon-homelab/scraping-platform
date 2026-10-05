package com.example.template.application.port.input

import com.example.template.domain.Todo
import java.util.UUID

interface TodoUseCase {
    fun create(title: String): Todo

    fun get(id: UUID): Todo

    fun list(): List<Todo>

    fun complete(id: UUID): Todo
}
