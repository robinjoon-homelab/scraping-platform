package com.example.template.collection.application.service

import com.example.template.collection.application.port.output.CheckpointStore
import com.example.template.collection.application.port.output.CollectedRecordStore
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.domain.run.CollectionBatch
import com.example.template.collection.domain.run.CollectionRequest
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.CollectionRunStatus
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// A separate bean so that starting and closing a run each get their own transaction when CollectionWorkerService calls
// them, keeping the connector call between them outside any transaction. Every change is a conditional update on the
// status that was read, and null means another worker or the lease expiry changed the run first.
@Transactional(readOnly = true)
class CollectionRunService(
    private val runs: CollectionRunRepository,
    private val records: CollectedRecordStore,
    private val checkpoints: CheckpointStore,
) {
    fun findPending(
        now: Instant,
        limit: Int,
    ): List<CollectionRun> = runs.findPending(now, limit)

    fun findExpiredLeases(
        now: Instant,
        limit: Int,
    ): List<CollectionRun> = runs.findExpiredLeases(now, limit)

    @Transactional
    fun start(
        run: CollectionRun,
        now: Instant,
        leaseUntil: Instant,
    ): CollectionRun? = runs.updateStarted(run.start(now), leaseUntil)

    // Closes a pending run as FAILED without a lease because there is nothing to collect for it.
    @Transactional
    fun refuse(
        run: CollectionRun,
        now: Instant,
        reason: String,
    ): CollectionRun? = runs.update(run.start(now).fail(now, reason), CollectionRunStatus.PENDING)

    // Builds the request for a run the worker just started. The task's checkpoint is sent only when it was written for the
    // run's task version: after a plan change the old cursor means nothing, so the source is collected from the start.
    fun requestFor(running: CollectionRun): CollectionRequest =
        CollectionRequest(running, checkpoints.find(running.task.taskId)?.takeIf { it.taskVersion == running.task.version })

    // Takes the running run the worker stored, not the run a batch carries. The run is closed first, as a conditional
    // update, and only the worker that closed it stores the batch: a null result (the lease expired and the run was closed
    // already) stores nothing, and the update's row lock keeps the lease expiry from closing the run while its records are
    // written. Records and checkpoint share this transaction with the update, so a store failure rolls the success back and
    // the run stays RUNNING until its lease expires; the checkpoint moves only together with the records it covers. A null
    // candidate checkpoint leaves the stored one unchanged.
    @Transactional
    fun complete(
        run: CollectionRun,
        batch: CollectionBatch,
        now: Instant,
    ): CollectionRun? {
        require(batch.request.run.id == run.id) { "batch must belong to the run being completed" }
        val closed = runs.update(run.succeed(now), CollectionRunStatus.RUNNING) ?: return null
        records.saveNew(batch.records)
        batch.nextCheckpoint?.let { checkpoints.save(it, run.id, now) }
        return closed
    }

    @Transactional
    fun fail(
        run: CollectionRun,
        now: Instant,
        reason: String,
    ): CollectionRun? = runs.update(run.fail(now, reason), CollectionRunStatus.RUNNING)
}
