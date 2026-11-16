package com.example.template.collection.application.service

import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.input.StaleTaskVersionException
import com.example.template.collection.application.port.output.CollectionControlStore
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.CollectorSupport
import com.example.template.collection.domain.validateCollectionConfiguration
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Transactional(readOnly = true)
class CollectionOperationStateService(
    private val tasks: CollectionTaskRepository,
    private val sources: SourceRepository,
    private val runs: CollectionRunRepository,
    private val control: CollectionControlStore,
    private val clock: Clock,
) {
    fun snapshot(taskId: UUID): TaskSnapshot {
        val task = tasks.findById(taskId) ?: throw CollectionTaskNotFoundException(taskId)
        val source = sources.findById(task.definition.sourceId) ?: throw SourceNotFoundException(task.definition.sourceId)
        return TaskSnapshot(task.id, task.version, task.definition, SourceSnapshot(source.id, source.definition, source.createdAt))
    }

    @Transactional
    fun enqueue(
        taskId: UUID,
        expectedVersion: Long,
        requestKey: String,
    ): CollectionRun {
        validateCollectionConfiguration(requestKey.matches(Regex("[A-Za-z0-9._-]{1,120}")), "requestKey must be 1..120 safe characters")
        control.lockTask(taskId)
        val prior = control.findRequest(taskId, requestKey)?.let { runs.findById(it) }
        if (prior != null) {
            if (prior.task.version != expectedVersion) throw StaleTaskVersionException(taskId)
            return prior
        }
        val snapshot = snapshot(taskId)
        if (snapshot.version != expectedVersion) throw StaleTaskVersionException(taskId)
        CollectorSupport.requireExecutable(snapshot.source.definition)
        val run = runs.insert(CollectionRun.pending(UUID.randomUUID(), snapshot, availableSlot(taskId)))
        control.saveRequest(taskId, requestKey, run.id)
        return run
    }

    private fun availableSlot(taskId: UUID): Instant {
        var slot = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)
        while (runs.existsForSlot(taskId, slot)) slot = slot.minusNanos(1000)
        return slot
    }
}
