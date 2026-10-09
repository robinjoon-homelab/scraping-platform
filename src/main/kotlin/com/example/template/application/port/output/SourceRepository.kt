package com.example.template.application.port.output

import com.example.template.domain.Source
import java.util.UUID

interface SourceRepository {
    fun insert(source: Source): Source

    fun findById(id: UUID): Source?

    fun findAll(
        offset: Int,
        limit: Int,
    ): List<Source>
}
