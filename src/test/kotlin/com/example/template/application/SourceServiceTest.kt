package com.example.template.application

import com.example.template.application.port.input.SourceNotFoundException
import com.example.template.application.service.SourceService
import com.example.template.domain.InvalidCollectionConfigurationException
import com.example.template.domain.Source
import com.example.template.domain.SourceDefinition
import com.example.template.domain.SourceKind
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
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repository = FakeSourceRepository()
    private val service = SourceService(repository, clock)

    @Test
    fun `creation trims a source name and uses the injected clock`() {
        val definition = sourceDefinition("  Official statistics  ")

        val created = service.create(definition)

        assertThat(created.definition.name).isEqualTo("Official statistics")
        assertThat(created.createdAt).isEqualTo(now)
        assertThat(repository.insertedSources).containsExactly(created)
        assertThat(definition.name).isEqualTo("  Official statistics  ")
    }

    @Test
    fun `creation returns canonical persistence values`() {
        val storedTime = Instant.parse("2026-10-05T00:00:00.123457Z")
        val canonicalRepository = FakeSourceRepository(storedTime)
        val canonicalService = SourceService(canonicalRepository, clock)

        val created = canonicalService.create(sourceDefinition("Official statistics"))

        assertThat(created.createdAt).isEqualTo(storedTime)
        assertThat(canonicalService.get(created.id)).isEqualTo(created)
    }

    @Test
    fun `get returns an existing source without inserting it`() {
        val existing = source("Official statistics")
        repository.seed(existing)

        assertThat(service.get(existing.id)).isEqualTo(existing)
        assertThat(repository.insertedSources).isEmpty()
    }

    @Test
    fun `get reports a missing source`() {
        assertThatThrownBy { service.get(UUID.randomUUID()) }
            .isInstanceOf(SourceNotFoundException::class.java)
    }

    @Test
    fun `list applies offset and limit without inserting sources`() {
        val first = source("Economy")
        val second = source("Development")
        val third = source("News")
        repository.seed(first, second, third)

        assertThat(service.list(1, 1)).containsExactly(second)
        assertThat(service.list(0, 100)).containsExactly(first, second, third)
        assertThat(service.list(3, 1)).isEmpty()
        assertThat(repository.insertedSources).isEmpty()
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

    private fun source(name: String): Source = Source(UUID.randomUUID(), sourceDefinition(name), now)

    private fun sourceDefinition(name: String): SourceDefinition = SourceDefinition(name, SourceKind.HTTP_JSON, "https://example.com")
}
