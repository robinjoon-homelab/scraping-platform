package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.model.CollectionPreview
import com.example.template.collection.application.model.CollectionValidationResult
import com.example.template.collection.application.model.CollectorCapability
import com.example.template.collection.application.port.input.CollectionOperationsUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class CollectionOperationsController(
    private val operations: CollectionOperationsUseCase,
) {
    @GetMapping("/api/collection/capabilities")
    fun capabilities(): List<CollectorCapability> = operations.capabilities()

    @GetMapping("/api/collection-tasks/{id}/validation")
    fun validate(
        @PathVariable id: UUID,
    ): CollectionValidationResult = operations.validate(id)

    @PostMapping("/api/collection-tasks/{id}/preview")
    fun preview(
        @PathVariable id: UUID,
    ): CollectionPreview = operations.preview(id)

    @PostMapping("/api/collection-tasks/{id}/runs")
    fun runNow(
        @PathVariable id: UUID,
        @RequestBody request: ImmediateRunRequest,
    ): CollectionRunResponse = CollectionRunResponse.from(operations.runNow(id, request.expectedVersion, request.requestKey))
}

data class ImmediateRunRequest(
    val expectedVersion: Long,
    val requestKey: String,
)
