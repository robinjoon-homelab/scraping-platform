package com.example.template.collection.application.port.input

import java.util.UUID

class CollectionRunNotFoundException(
    id: UUID,
) : RuntimeException("Collection run not found: $id")
