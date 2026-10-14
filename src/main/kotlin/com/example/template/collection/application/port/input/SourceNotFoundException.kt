package com.example.template.collection.application.port.input

import java.util.UUID

class SourceNotFoundException(
    id: UUID,
) : RuntimeException("Source not found: $id")
