package com.example.template.collection.application.port.output

import java.time.Instant
import java.util.UUID

class CollectionRunSlotTakenException(
    taskId: UUID,
    scheduledFor: Instant,
    cause: Throwable,
) : RuntimeException("Collection run slot is already taken: task $taskId at $scheduledFor", cause)
