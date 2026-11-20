package com.example.template.todo.domain.todo

class InvalidTodoTitleException(
    message: String,
) : IllegalArgumentException(message)
