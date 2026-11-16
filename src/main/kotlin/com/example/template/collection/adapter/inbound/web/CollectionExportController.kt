package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.model.ExportPage
import com.example.template.collection.application.model.ExportStatus
import com.example.template.collection.application.port.input.CollectionExportUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/collection-tasks/{id}/export")
class CollectionExportController(
    private val exports: CollectionExportUseCase,
) {
    @GetMapping
    fun status(
        @PathVariable id: UUID,
    ): ExportStatus = exports.status(id)

    @PutMapping
    fun configure(
        @PathVariable id: UUID,
        @RequestBody request: ExportConfigurationRequest,
    ): ExportStatus = exports.configure(id, request.enabled)

    @GetMapping("/records")
    fun pull(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") after: Long,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ExportPage = exports.pull(id, after, limit)
}

data class ExportConfigurationRequest(
    val enabled: Boolean,
)
