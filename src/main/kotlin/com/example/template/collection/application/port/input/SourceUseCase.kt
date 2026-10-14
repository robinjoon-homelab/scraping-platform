package com.example.template.collection.application.port.input

import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.source.SourceDefinition
import java.util.UUID

interface SourceUseCase {
    fun create(definition: SourceDefinition): Source

    fun get(id: UUID): Source

    fun list(
        offset: Int,
        limit: Int,
    ): List<Source>
}
