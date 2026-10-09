package com.example.template.application.port.input

import java.util.UUID

class CollectionTaskNotFoundException(
    id: UUID,
) : RuntimeException("Collection task not found: $id")
