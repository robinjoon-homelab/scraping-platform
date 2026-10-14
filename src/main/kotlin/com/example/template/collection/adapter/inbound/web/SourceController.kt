package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.port.input.SourceUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/sources")
class SourceController(
    private val sourceUseCase: SourceUseCase,
) {
    @PostMapping
    fun create(
        @RequestBody request: CreateSourceRequest,
    ): ResponseEntity<SourceResponse> {
        val source = sourceUseCase.create(request.toDefinition())
        return ResponseEntity.created(URI.create("/api/sources/${source.id}")).body(SourceResponse.from(source))
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): SourceResponse = SourceResponse.from(sourceUseCase.get(id))

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<SourceResponse> = sourceUseCase.list(offset, limit).map(SourceResponse::from)
}
