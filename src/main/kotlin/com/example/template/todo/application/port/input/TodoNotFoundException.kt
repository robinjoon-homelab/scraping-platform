package com.example.template.todo.application.port.input

import com.example.template.todo.domain.todo.TodoId

class TodoNotFoundException(
    id: TodoId,
) : RuntimeException("Todo not found: $id")
