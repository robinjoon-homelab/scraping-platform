package com.example.template.application.port.input

import java.util.UUID

class StaleTaskVersionException(
    id: UUID,
) : RuntimeException("Collection task version is stale: $id")
