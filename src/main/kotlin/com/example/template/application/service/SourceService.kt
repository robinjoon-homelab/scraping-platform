package com.example.template.application.service

import com.example.template.application.port.input.SourceNotFoundException
import com.example.template.application.port.input.SourceUseCase
import com.example.template.application.port.output.SourceRepository
import com.example.template.domain.Source
import com.example.template.domain.SourceDefinition
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
            Source(
                id = UUID.randomUUID(),
                definition = definition.copy(name = definition.name.trim()),
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
