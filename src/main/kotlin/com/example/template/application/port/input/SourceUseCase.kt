package com.example.template.application.port.input

import com.example.template.domain.Source
import com.example.template.domain.SourceDefinition
import java.util.UUID

interface SourceUseCase {
    fun create(definition: SourceDefinition): Source

    fun get(id: UUID): Source

    fun list(
        offset: Int,
        limit: Int,
    ): List<Source>
}
