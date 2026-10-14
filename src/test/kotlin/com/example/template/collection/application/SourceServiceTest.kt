package com.example.template.collection.application

import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.application.service.SourceService
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SourceServiceTest {
    private val now = Instant.parse("2026-10-05T00:00:00.123456789Z")
    private val repository = mockk<SourceRepository>()
    private val service = SourceService(repository, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `creation returns a trimmed source name with the clock time`() {
        val definition = sourceDefinition("  Official statistics  ")
        every { repository.insert(any()) } answers { firstArg() }

        val created = service.create(definition)

        assertThat(created.definition.name).isEqualTo("Official statistics")
        assertThat(created.createdAt).isEqualTo(now)
        assertThat(definition.name).isEqualTo("  Official statistics  ")
    }

    @Test
    fun `creation returns the values the repository stored`() {
        val storedTime = Instant.parse("2026-10-05T00:00:00.123457Z")
        every { repository.insert(any()) } answers { Source.restore(firstArg<Source>().id, firstArg<Source>().definition, storedTime) }

        val created = service.create(sourceDefinition("Official statistics"))

        assertThat(created.createdAt).isEqualTo(storedTime)
    }

    @Test
    fun `get returns an existing source and reports a missing one`() {
        val existing = source("Official statistics")
        val missingId = UUID.randomUUID()
        every { repository.findById(existing.id) } returns existing
        every { repository.findById(missingId) } returns null

        assertThat(service.get(existing.id)).isEqualTo(existing)
        assertThatThrownBy { service.get(missingId) }
            .isInstanceOf(SourceNotFoundException::class.java)
            .hasMessage("Source not found: $missingId")
    }

    @Test
    fun `list returns the repository page for valid offset and limit boundaries`() {
        val second = source("Development")
        every { repository.findAll(1, 1) } returns listOf(second)
        every { repository.findAll(1, 100) } returns emptyList()

        assertThat(service.list(1, 1)).containsExactly(second)
        assertThat(service.list(1, 100)).isEmpty()
    }

    @ParameterizedTest
    @CsvSource("-1, 1", "0, 0", "0, 101")
    fun `list rejects invalid pagination`(
        offset: Int,
        limit: Int,
    ) {
        assertThatThrownBy { service.list(offset, limit) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    private fun source(name: String): Source = Source.create(UUID.randomUUID(), sourceDefinition(name), now)

    private fun sourceDefinition(name: String): SourceDefinition = SourceDefinition(name, SourceKind.HTTP_JSON, "https://example.com")
}
