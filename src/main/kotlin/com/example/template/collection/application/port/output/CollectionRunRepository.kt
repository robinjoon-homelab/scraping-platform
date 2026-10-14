package com.example.template.collection.application.port.output

import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.CollectionRunStatus
import java.time.Instant
import java.util.UUID

interface CollectionRunRepository {
    // Throws CollectionRunSlotTakenException when the task already has a run for the same scheduled time.
    fun insert(run: CollectionRun): CollectionRun

    fun findById(id: UUID): CollectionRun?

    fun existsForSlot(
        taskId: UUID,
        scheduledFor: Instant,
    ): Boolean

    // Stores the run only while the stored status is still expectedStatus. Returns null when the run is missing, its stored
    // status differs, or expectedStatus is already final, so a stale object never overwrites a newer state.
    fun update(
        run: CollectionRun,
        expectedStatus: CollectionRunStatus,
    ): CollectionRun?
}
