package com.example.template.collection.application.port.input

import java.util.UUID

class CollectedRecordNotFoundException(
    id: UUID,
) : RuntimeException("Collected record not found: $id")
