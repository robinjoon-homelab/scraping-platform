package com.example.template.collection.application

import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.input.StaleTaskVersionException
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.application.service.CollectionTaskService
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
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

class CollectionTaskServiceTest {
    private val now = Instant.parse("2026-10-05T00:00:00.123456789Z")
    private val repository = mockk<CollectionTaskRepository>()
    private val sources = mockk<SourceRepository>()
    private val service = CollectionTaskService(repository, sources, Clock.fixed(now, ZoneOffset.UTC))
    private val source = Source.create(UUID.randomUUID(), SourceDefinition("Economy", SourceKind.HTTP_JSON, "https://example.com"), now)

    @Test
    fun `creation returns a trimmed disabled version zero task with the clock time`() {
        val definition = definition("  Consumer prices  ")
        every { sources.findById(source.id) } returns source
        every { repository.insert(any()) } answers { firstArg() }

        val created = service.create(definition)

        assertThat(created.definition.name).isEqualTo("Consumer prices")
        assertThat(created.enabled).isFalse()
        assertThat(created.version).isZero()
        assertThat(created.createdAt).isEqualTo(now)
        assertThat(definition.name).isEqualTo("  Consumer prices  ")
    }

    @Test
    fun `creation returns the values the repository stored`() {
        val stored = CollectionTask.create(UUID.randomUUID(), definition(), Instant.parse("2026-10-05T00:00:00.123457Z"))
        every { sources.findById(source.id) } returns source
        every { repository.insert(any()) } returns stored

        assertThat(service.create(definition())).isEqualTo(stored)
    }

    @Test
    fun `creation rejects an unknown source`() {
        val missingSourceId = UUID.randomUUID()
        every { sources.findById(missingSourceId) } returns null

        assertThatThrownBy { service.create(definition().copy(sourceId = missingSourceId)) }
            .isInstanceOf(SourceNotFoundException::class.java)
    }

    @Test
    fun `get returns an existing task and reports a missing one`() {
        val existing = task()
        val missingId = UUID.randomUUID()
        every { repository.findById(existing.id) } returns existing
        every { repository.findById(missingId) } returns null

        assertThat(service.get(existing.id)).isEqualTo(existing)
        assertThatThrownBy { service.get(missingId) }
            .isInstanceOf(CollectionTaskNotFoundException::class.java)
            .hasMessage("Collection task not found: $missingId")
    }

    @Test
    fun `missing tasks cannot be replaced or enabled`() {
        val missingId = UUID.randomUUID()
        every { repository.findById(missingId) } returns null

        assertThatThrownBy { service.replace(missingId, 0, definition()) }
            .isInstanceOf(CollectionTaskNotFoundException::class.java)
        assertThatThrownBy { service.setEnabled(missingId, 0, true) }
            .isInstanceOf(CollectionTaskNotFoundException::class.java)
    }

    @Test
    fun `replacement returns the trimmed definition as the next version and keeps identity and state`() {
        val original = task().changeEnabled(true)
        every { repository.findById(original.id) } returns original
        every { sources.findById(source.id) } returns source
        every { repository.update(any(), 1) } answers { firstArg() }

        val revised = service.replace(original.id, 1, definition("  Producer prices  "))

        assertThat(revised.id).isEqualTo(original.id)
        assertThat(revised.definition.name).isEqualTo("Producer prices")
        assertThat(revised.createdAt).isEqualTo(original.createdAt)
        assertThat(revised.enabled).isTrue()
        assertThat(revised.version).isEqualTo(2)
    }

    @Test
    fun `replacement rejects an unknown source`() {
        val original = task()
        val missingSourceId = UUID.randomUUID()
        every { repository.findById(original.id) } returns original
        every { sources.findById(missingSourceId) } returns null

        assertThatThrownBy { service.replace(original.id, 0, definition().copy(sourceId = missingSourceId)) }
            .isInstanceOf(SourceNotFoundException::class.java)
    }

    @Test
    fun `enabling and disabling return the next version with the requested state`() {
        val original = task()
        val enabledTask = original.changeEnabled(true)
        every { repository.findById(original.id) } returns original andThen enabledTask
        every { repository.update(any(), any()) } answers { firstArg() }

        val enabled = service.setEnabled(original.id, 0, true)
        val disabled = service.setEnabled(original.id, 1, false)

        assertThat(enabled).isEqualTo(enabledTask)
        assertThat(disabled.enabled).isFalse()
        assertThat(disabled.version).isEqualTo(2)
        assertThat(disabled.definition).isEqualTo(original.definition)
    }

    @Test
    fun `stale expected versions cannot replace or enable a task`() {
        val current = task().changeEnabled(true)
        every { repository.findById(current.id) } returns current

        assertThatThrownBy { service.replace(current.id, 0, definition("Changed")) }
            .isInstanceOf(StaleTaskVersionException::class.java)
        assertThatThrownBy { service.setEnabled(current.id, 0, false) }
            .isInstanceOf(StaleTaskVersionException::class.java)
    }

    @Test
    fun `an update the repository rejects is reported as stale`() {
        val original = task()
        every { repository.findById(original.id) } returns original
        every { sources.findById(source.id) } returns source
        every { repository.update(any(), 0) } returns null

        assertThatThrownBy { service.replace(original.id, 0, definition("Changed")) }
            .isInstanceOf(StaleTaskVersionException::class.java)
        assertThatThrownBy { service.setEnabled(original.id, 0, true) }
            .isInstanceOf(StaleTaskVersionException::class.java)
    }

    @Test
    fun `negative expected versions are invalid configuration`() {
        val id = UUID.randomUUID()

        assertThatThrownBy { service.replace(id, -1, definition()) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { service.setEnabled(id, -1, true) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `list returns the repository page for valid offset and limit boundaries`() {
        val second = task()
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

    private fun task(): CollectionTask = CollectionTask.create(UUID.randomUUID(), definition(), now)

    private fun definition(name: String = "Consumer prices"): TaskDefinition =
        TaskDefinition(name, source.id, CollectionTarget("/observations", mapOf("series" to "CPI")), IntervalSchedule(3600, now))
}
