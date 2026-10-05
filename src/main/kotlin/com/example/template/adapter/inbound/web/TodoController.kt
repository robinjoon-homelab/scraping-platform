package com.example.template.adapter.inbound.web

import com.example.template.application.port.input.TodoUseCase
import com.example.template.domain.Todo
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/todos")
class TodoController(
    private val todoUseCase: TodoUseCase,
) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateTodoRequest,
    ): ResponseEntity<TodoResponse> {
        val todo = todoUseCase.create(request.title)
        return ResponseEntity.created(URI.create("/api/todos/${todo.id}")).body(TodoResponse.from(todo))
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): TodoResponse = TodoResponse.from(todoUseCase.get(id))

    @GetMapping
    fun list(): List<TodoResponse> = todoUseCase.list().map(TodoResponse::from)

    @PatchMapping("/{id}/complete")
    fun complete(
        @PathVariable id: UUID,
    ): TodoResponse = TodoResponse.from(todoUseCase.complete(id))
}

data class CreateTodoRequest(
    @field:NotBlank(message = "title must not be blank")
    @field:Size(max = 200, message = "title must be at most 200 characters")
    val title: String,
)

data class TodoResponse(
    val id: UUID,
    val title: String,
    val completed: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(todo: Todo): TodoResponse =
            TodoResponse(
                id = todo.id,
                title = todo.title,
                completed = todo.completed,
                createdAt = todo.createdAt,
            )
    }
}
