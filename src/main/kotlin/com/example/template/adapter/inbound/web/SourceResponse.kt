package com.example.template.adapter.inbound.web

import com.example.template.domain.Source
import com.example.template.domain.SourceDefinition
import java.time.Instant
import java.util.UUID

data class SourceResponse(
    val id: UUID,
    val definition: SourceDefinition,
    val createdAt: Instant,
) {
    companion object {
        fun from(source: Source): SourceResponse = SourceResponse(source.id, source.definition, source.createdAt)
    }
}
