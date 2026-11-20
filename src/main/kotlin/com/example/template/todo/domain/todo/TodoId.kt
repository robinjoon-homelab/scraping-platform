package com.example.template.todo.domain.todo

import java.util.UUID

@JvmInline
value class TodoId(
    val value: UUID,
) {
    // Messages and logs keep the plain UUID format of the public API.
    override fun toString(): String = value.toString()
}
