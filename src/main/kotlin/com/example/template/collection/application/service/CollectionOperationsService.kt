package com.example.template.collection.application.service

import com.example.template.collection.application.model.CollectionPreview
import com.example.template.collection.application.model.CollectionValidationResult
import com.example.template.collection.application.model.CollectorCapability
import com.example.template.collection.application.port.input.CollectionOperationsUseCase
import com.example.template.collection.application.port.input.CollectionPreviewFailedException
import com.example.template.collection.application.port.output.CollectionConnectorPort
import com.example.template.collection.domain.run.CollectionRequest
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.source.CollectorSupport
import com.example.template.collection.domain.source.SourceKind
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CollectionOperationsService(
    private val state: CollectionOperationStateService,
    private val connector: CollectionConnectorPort,
    private val clock: Clock,
) : CollectionOperationsUseCase {
    override fun capabilities(): List<CollectorCapability> =
        SourceKind.entries.map {
            CollectorCapability(it, it == SourceKind.HTTP_JSON, false, "whole JSON response")
        }

    override fun validate(taskId: UUID): CollectionValidationResult {
        val issues = CollectorSupport.issues(state.snapshot(taskId).source.definition)
        return CollectionValidationResult(issues.isEmpty(), issues)
    }

    override fun preview(taskId: UUID): CollectionPreview {
        val snapshot = state.snapshot(taskId)
        CollectorSupport.requireExecutable(snapshot.source.definition)
        val now = Instant.now(clock)
        val request = CollectionRequest(CollectionRun.pending(UUID.randomUUID(), snapshot, now).start(now), maxRecords = 1)
        val result = runCatching { connector.collect(request) }
        val failure = result.exceptionOrNull()
        if (failure is Error || failure is InterruptedException) throw failure
        if (failure != null) throw CollectionPreviewFailedException(failure)
        val batch = result.getOrThrow()
        require(batch.request == request) { "Preview connector returned a foreign request" }
        return CollectionPreview(batch.records, "whole JSON response")
    }

    override fun runNow(
        taskId: UUID,
        expectedVersion: Long,
        requestKey: String,
    ): CollectionRun = state.enqueue(taskId, expectedVersion, requestKey)
}
