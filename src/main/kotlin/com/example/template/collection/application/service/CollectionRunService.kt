package com.example.template.collection.application.service

import com.example.template.collection.application.port.output.CollectionRunRepository
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

    // Takes the running run the worker stored, not the run a batch carries. The batch records and its candidate checkpoint
    // are discarded by the worker: nothing stores them yet. Storing them belongs in this transaction, before the run
    // succeeds, so that a run never succeeds without its records.
    @Transactional
    fun complete(
        run: CollectionRun,
        now: Instant,
    ): CollectionRun? = runs.update(run.succeed(now), CollectionRunStatus.RUNNING)

    @Transactional
    fun fail(
        run: CollectionRun,
        now: Instant,
        reason: String,
    ): CollectionRun? = runs.update(run.fail(now, reason), CollectionRunStatus.RUNNING)
}
