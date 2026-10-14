package com.example.template.collection.application.service

import com.example.template.collection.application.port.input.CollectionWorkerUseCase
import com.example.template.collection.application.port.output.CollectionConnectorException
import com.example.template.collection.application.port.output.CollectionConnectorPort
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.run.CollectionRequest
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.MAX_FAILURE_LENGTH
import com.example.template.collection.domain.validateCollectionConfiguration
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

// A run is started and closed in two CollectionRunService transactions, and the connector is called between them outside
// any transaction so that a slow source never holds a database connection. NOT_SUPPORTED keeps a caller's transaction
// from merging those steps. A run's failure is recorded on that run without stopping the others; infrastructure failures,
// JVM errors and interruption still end the call, and a run they leave RUNNING is closed once its lease expires.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CollectionWorkerService(
    private val runs: CollectionRunService,
    private val connector: CollectionConnectorPort,
    private val clock: Clock,
    private val leaseDuration: Duration,
) : CollectionWorkerUseCase {
    init {
        require(leaseDuration > Duration.ZERO) { "lease duration must be positive" }
    }

    override fun runPending(limit: Int): Int {
        validateLimit(limit)
        return runs.findPending(Instant.now(clock), limit).count { run -> isolated(run) { process(run) } == true }
    }

    // Expired runs are not retried; a new attempt needs a new run.
    override fun expireLeases(limit: Int): Int {
        validateLimit(limit)
        val now = Instant.now(clock)
        return runs.findExpiredLeases(now, limit).count { run ->
            val closed = isolated(run) { runs.fail(run, now, LEASE_EXPIRED) }
            if (closed != null) logger.log(System.Logger.Level.WARNING, "Closed collection run ${run.id} after its lease expired")
            closed != null
        }
    }

    // Returns whether this worker took the run; false means another worker started or closed it first. The run is always
    // closed from the running state this worker stored, never from the run a connector returned.
    private fun process(pending: CollectionRun): Boolean {
        val now = Instant.now(clock)
        if (pending.task.source.definition.credentialRef != null) {
            return runs.refuse(pending, now, CREDENTIAL_UNSUPPORTED) != null
        }
        val running = runs.start(pending, now, now.plus(leaseDuration)) ?: return false
        val failure = collect(running)
        val finishedAt = Instant.now(clock)
        val closed = if (failure == null) runs.complete(running, finishedAt) else runs.fail(running, finishedAt, failure)
        if (closed == null) {
            logger.log(System.Logger.Level.WARNING, "Discarded the result of collection run ${running.id} closed before it finished")
        } else if (closed.failureReason != null) {
            logger.log(System.Logger.Level.WARNING, "Collection run ${running.id} failed: ${closed.failureReason}")
        }
        return true
    }

    // Returns the failure reason, or null when the connector returned a batch for exactly the request it was given.
    // runCatching turns every connector exception into a run failure. JVM errors and interruption are not failures of
    // the source, so they are rethrown and the lease expiry closes the run instead.
    private fun collect(running: CollectionRun): String? {
        val request = CollectionRequest(running)
        val result = runCatching { connector.collect(request) }
        val failure = result.exceptionOrNull()
        if (failure is Error || failure is InterruptedException) throw failure
        if (failure != null) return failureReason(failure)
        return if (isSameRequest(request, result.getOrThrow().request)) null else FOREIGN_BATCH
    }

    // The batch invariants were checked against the request it carries, so that request must be the one sent. Comparing
    // only the run ID would let a rebuilt run change the start time or snapshot, or a rebuilt request raise the limit.
    private fun isSameRequest(
        sent: CollectionRequest,
        returned: CollectionRequest,
    ): Boolean = isSameRun(sent.run, returned.run) && returned.checkpoint == sent.checkpoint && returned.maxRecords == sent.maxRecords

    private fun isSameRun(
        sent: CollectionRun,
        returned: CollectionRun,
    ): Boolean =
        returned.id == sent.id &&
            returned.status == sent.status &&
            returned.task == sent.task &&
            returned.scheduledFor == sent.scheduledFor &&
            returned.startedAt == sent.startedAt &&
            returned.finishedAt == sent.finishedAt &&
            returned.failureReason == sent.failureReason

    // Only CollectionConnectorException messages are written for the reason. Any other exception, including a domain
    // validation failure raised inside the connector, may quote a response body or a secret, so only its type is kept.
    private fun failureReason(failure: Throwable): String {
        val described = (failure as? CollectionConnectorException)?.message?.trim()
        val reason = if (described.isNullOrEmpty()) "connector failed with ${failure.javaClass.name}" else described
        return reason.take(MAX_FAILURE_LENGTH)
    }

    private fun <T : Any> isolated(
        run: CollectionRun,
        step: () -> T?,
    ): T? =
        try {
            step()
        } catch (exception: InvalidCollectionConfigurationException) {
            // The message is not logged: only CollectionConnectorException messages are known to be free of secrets.
            logger.log(System.Logger.Level.WARNING, "Skipped collection run ${run.id} after ${exception.javaClass.name}")
            null
        }

    private fun validateLimit(limit: Int) =
        validateCollectionConfiguration(limit in 1..MAX_RUNS_PER_CALL, "Limit must be between 1 and $MAX_RUNS_PER_CALL")

    private companion object {
        const val MAX_RUNS_PER_CALL = 1000
        const val LEASE_EXPIRED = "lease expired"
        const val CREDENTIAL_UNSUPPORTED = "credential lookup not supported"
        const val FOREIGN_BATCH = "connector returned a batch for a different request"
        val logger: System.Logger = System.getLogger(CollectionWorkerService::class.java.name)
    }
}
