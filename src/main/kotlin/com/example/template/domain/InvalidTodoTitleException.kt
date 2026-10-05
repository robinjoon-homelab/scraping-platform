package com.example.template.domain

class InvalidTodoTitleException(
    message: String,
) : IllegalArgumentException(message)
