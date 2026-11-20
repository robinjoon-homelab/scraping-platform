package com.example.template.todo.application.service

import com.example.template.todo.application.port.input.TodoNotFoundException
import com.example.template.todo.application.port.input.TodoUseCase
import com.example.template.todo.application.port.output.TodoRepository
import com.example.template.todo.domain.todo.Todo
import com.example.template.todo.domain.todo.TodoId
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Transactional(readOnly = true)
class TodoService(
    private val repository: TodoRepository,
    private val clock: Clock,
) : TodoUseCase {
    @Transactional
    override fun create(title: String): Todo = repository.add(Todo.create(TodoId(UUID.randomUUID()), title, Instant.now(clock)))

    override fun get(id: TodoId): Todo = repository.findById(id) ?: throw TodoNotFoundException(id)

    override fun list(): List<Todo> = repository.findAll()

    @Transactional
    override fun complete(id: TodoId): Todo {
        val todo = get(id)
        return if (todo.completed) todo else repository.update(todo.complete())
    }
}
