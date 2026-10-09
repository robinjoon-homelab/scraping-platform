package com.example.template.adapter.inbound.web

import com.example.template.application.port.input.CollectionTaskUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/collection-tasks")
class CollectionTaskController(
    private val taskUseCase: CollectionTaskUseCase,
) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: TaskDefinitionRequest,
    ): ResponseEntity<CollectionTaskResponse> {
        val task = taskUseCase.create(request.toDefinition())
        return ResponseEntity.created(URI.create("/api/collection-tasks/${task.id}")).body(CollectionTaskResponse.from(task))
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): CollectionTaskResponse = CollectionTaskResponse.from(taskUseCase.get(id))

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<CollectionTaskResponse> = taskUseCase.list(offset, limit).map(CollectionTaskResponse::from)

    @PutMapping("/{id}")
    fun replace(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReplaceTaskRequest,
    ): CollectionTaskResponse =
        CollectionTaskResponse.from(taskUseCase.replace(id, requireNotNull(request.expectedVersion), request.definition.toDefinition()))

    @PatchMapping("/{id}/enabled")
    fun setEnabled(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SetTaskEnabledRequest,
    ): CollectionTaskResponse =
        CollectionTaskResponse.from(taskUseCase.setEnabled(id, requireNotNull(request.expectedVersion), requireNotNull(request.enabled)))
}
