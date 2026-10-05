package com.example.template.application.service

import com.example.template.application.port.input.TodoNotFoundException
import com.example.template.application.port.input.TodoUseCase
import com.example.template.application.port.output.TodoRepository
import com.example.template.domain.Todo
import java.time.Clock
import java.time.Instant
import java.util.UUID

class TodoService(
    private val repository: TodoRepository,
    private val clock: Clock,
) : TodoUseCase {
    override fun create(title: String): Todo =
        repository.save(
            Todo(
                id = UUID.randomUUID(),
                title = title.trim(),
                createdAt = Instant.now(clock),
            ),
        )

    override fun get(id: UUID): Todo = repository.findById(id) ?: throw TodoNotFoundException(id)

    override fun list(): List<Todo> = repository.findAll()

    override fun complete(id: UUID): Todo = repository.save(get(id).complete())
}
