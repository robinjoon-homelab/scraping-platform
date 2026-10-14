package com.example.template.collection.application.port.output

import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.CollectionRunStatus
import java.time.Instant
import java.util.UUID

// The lease is a stored worker-ownership deadline, not run state: it is written only when a run starts, cleared when a run
// reaches a final state, and never renewed.
interface CollectionRunRepository {
    // Throws CollectionRunSlotTakenException when the task already has a run for the same scheduled time.
    fun insert(run: CollectionRun): CollectionRun

    fun findById(id: UUID): CollectionRun?

    fun existsForSlot(
        taskId: UUID,
        scheduledFor: Instant,
    ): Boolean

    // Pending runs scheduled at or before now, earliest scheduled time first.
    fun findPending(
        now: Instant,
        limit: Int,
    ): List<CollectionRun>

    // Running runs whose lease ended before now, earliest lease first. A running run without a lease is included because
    // no worker can own it.
    fun findExpiredLeases(
        now: Instant,
        limit: Int,
    ): List<CollectionRun>

    // Stores a just-started run with its lease only while the stored status is still PENDING. Returns null when the run is
    // missing or another worker started or closed it first. The run must be RUNNING.
    fun updateStarted(
        run: CollectionRun,
        leaseUntil: Instant,
    ): CollectionRun?

    // Stores the run only while the stored status is still expectedStatus. Returns null when the run is missing, its stored
    // status differs, or expectedStatus is already final, so a stale object never overwrites a newer state. A final run
    // clears the lease; otherwise the stored lease is kept, so a run should become RUNNING through updateStarted.
    fun update(
        run: CollectionRun,
        expectedStatus: CollectionRunStatus,
    ): CollectionRun?
}
