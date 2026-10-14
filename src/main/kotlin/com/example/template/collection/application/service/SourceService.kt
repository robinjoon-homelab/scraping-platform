package com.example.template.collection.application.service

import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.input.SourceUseCase
import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.source.SourceDefinition
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Transactional(readOnly = true)
class SourceService(
    private val repository: SourceRepository,
    private val clock: Clock,
) : SourceUseCase {
    @Transactional
    override fun create(definition: SourceDefinition): Source =
        repository.insert(
            Source.create(
                id = UUID.randomUUID(),
                definition = definition,
                createdAt = Instant.now(clock),
            ),
        )

    override fun get(id: UUID): Source = repository.findById(id) ?: throw SourceNotFoundException(id)

    override fun list(
        offset: Int,
        limit: Int,
    ): List<Source> {
        validateCollectionPage(offset, limit)
        return repository.findAll(offset, limit)
    }
}
