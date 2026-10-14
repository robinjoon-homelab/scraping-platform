package com.example.template.collection.application.service

import com.example.template.collection.application.port.input.CollectionSchedulingUseCase
import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.output.CollectionRunSlotTakenException
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.validateCollectionConfiguration
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

// Each task commits or rolls back alone inside CollectionSlotService, so a failure of one task neither rolls back nor stops
// the others. Unexpected infrastructure failures still propagate and end the call.
// NOT_SUPPORTED keeps a caller's transaction from swallowing those per-task transactions into one.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CollectionSchedulingService(
    private val slots: CollectionSlotService,
    private val clock: Clock,
) : CollectionSchedulingUseCase {
    override fun scheduleDue(limit: Int): Int {
        validateCollectionConfiguration(limit in 1..MAX_TASKS_PER_CALL, "Limit must be between 1 and $MAX_TASKS_PER_CALL")
        val now = Instant.now(clock)
        slots.findUnscheduled(limit).forEach { task -> isolated(task) { slots.initialize(task, now) } }
        return slots.findDue(now, limit).count { task -> isolated(task) { slots.claim(task, now) } != null }
    }

    private fun <T : Any> isolated(
        task: CollectionTask,
        step: () -> T?,
    ): T? =
        try {
            step()
        } catch (exception: CollectionRunSlotTakenException) {
            skip(task, exception)
        } catch (exception: SourceNotFoundException) {
            skip(task, exception)
        } catch (exception: InvalidCollectionConfigurationException) {
            skip(task, exception)
        }

    private fun <T> skip(
        task: CollectionTask,
        exception: RuntimeException,
    ): T? {
        logger.log(System.Logger.Level.WARNING, "Skipped scheduling collection task ${task.id}", exception)
        return null
    }

    private companion object {
        const val MAX_TASKS_PER_CALL = 1000
        val logger: System.Logger = System.getLogger(CollectionSchedulingService::class.java.name)
    }
}
