package com.example.template.collection.application.port.output

import com.example.template.collection.domain.source.Source
import java.util.UUID

interface SourceRepository {
    fun insert(source: Source): Source

    fun findById(id: UUID): Source?

    fun findAll(
        offset: Int,
        limit: Int,
    ): List<Source>
}
