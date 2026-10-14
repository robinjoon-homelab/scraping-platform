package com.example.template.collection.application.port.output

import com.example.template.collection.domain.run.Checkpoint
import java.time.Instant
import java.util.UUID

interface CheckpointStore {
    fun find(taskId: UUID): Checkpoint?

    /**
     * Makes the checkpoint the task's current one, recording the run that produced it. A checkpoint of an older task
     * version never replaces a stored checkpoint of a newer version.
     */
    fun save(
        checkpoint: Checkpoint,
        runId: UUID,
        at: Instant,
    )
}
