package com.example.template.collection.adapter.inbound.scheduler

import com.example.template.collection.application.port.input.CollectionSchedulingUseCase
import com.example.template.collection.application.port.input.CollectionWorkerUseCase
import org.springframework.scheduling.annotation.Scheduled

// Drives the collection cycle on a fixed delay so that cycles never overlap within one instance. Each step is isolated:
// a failure is logged by exception type only, because messages may quote a response body or a secret, and the next step
// and the next cycle still run. The order lets a lease that expired and a slot that came due be handled in the same cycle.
class CollectionScheduler(
    private val worker: CollectionWorkerUseCase,
    private val scheduling: CollectionSchedulingUseCase,
    private val batchSize: Int,
) {
    @Scheduled(fixedDelayString = "\${scraping.worker.interval}")
    fun runCycle() {
        step("expire leases") { worker.expireLeases(batchSize) }
        step("schedule due tasks") { scheduling.scheduleDue(batchSize) }
        step("run pending runs") { worker.runPending(batchSize) }
    }

    private fun step(
        name: String,
        action: () -> Int,
    ) {
        val result = runCatching(action)
        val failure = result.exceptionOrNull()
        // JVM errors and interruption must stop the thread rather than be logged and retried on the next cycle.
        if (failure is Error || failure is InterruptedException) throw failure
        if (failure != null) {
            logger.log(System.Logger.Level.ERROR, "Collection cycle step '$name' failed with ${failure.javaClass.name}")
        } else if (result.getOrThrow() > 0) {
            logger.log(System.Logger.Level.INFO, "Collection cycle step '$name' handled ${result.getOrThrow()}")
        }
    }

    private companion object {
        val logger: System.Logger = System.getLogger(CollectionScheduler::class.java.name)
    }
}
