package com.example.template.application.port.input

import java.util.UUID

class TodoNotFoundException(
    id: UUID,
) : RuntimeException("Todo not found: $id")
