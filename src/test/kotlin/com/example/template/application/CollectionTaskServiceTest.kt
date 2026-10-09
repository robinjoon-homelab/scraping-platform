package com.example.template.application

import com.example.template.application.port.input.CollectionTaskNotFoundException
import com.example.template.application.port.input.SourceNotFoundException
import com.example.template.application.port.input.StaleTaskVersionException
import com.example.template.application.service.CollectionTaskService
import com.example.template.domain.CollectionTarget
import com.example.template.domain.CollectionTask
import com.example.template.domain.IntervalSchedule
import com.example.template.domain.InvalidCollectionConfigurationException
import com.example.template.domain.Source
import com.example.template.domain.SourceDefinition
import com.example.template.domain.SourceKind
import com.example.template.domain.TaskDefinition
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
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repository = FakeCollectionTaskRepository()
    private val source = Source(UUID.randomUUID(), SourceDefinition("Economy", SourceKind.HTTP_JSON, "https://example.com"), now)
    private val sources = FakeSourceRepository().apply { seed(source) }
    private val service = CollectionTaskService(repository, sources, clock)

    @Test
    fun `creation trims the name and persists a disabled version zero task with the clock time`() {
        val definition = definition("  Consumer prices  ")

        val created = service.create(definition)

        assertThat(created.definition.name).isEqualTo("Consumer prices")
        assertThat(created.enabled).isFalse()
        assertThat(created.version).isZero()
        assertThat(created.createdAt).isEqualTo(now)
        assertThat(repository.insertedTasks).containsExactly(created)
        assertThat(definition.name).isEqualTo("  Consumer prices  ")
    }

    @Test
    fun `creation returns canonical persistence values`() {
        val storedTime = Instant.parse("2026-10-05T00:00:00.123457Z")
        val canonicalRepository = FakeCollectionTaskRepository(storedTime)
        val canonicalService = CollectionTaskService(canonicalRepository, sources, clock)

        val created = canonicalService.create(definition())

        assertThat(created.createdAt).isEqualTo(storedTime)
        assertThat(canonicalService.get(created.id)).isEqualTo(created)
    }

    @Test
    fun `creation rejects an unknown source without persisting a task`() {
        val definition = definition().copy(sourceId = UUID.randomUUID())

        assertThatThrownBy { service.create(definition) }
            .isInstanceOf(SourceNotFoundException::class.java)
        assertThat(repository.insertedTasks).isEmpty()
    }

    @Test
    fun `get returns an existing task without inserting it`() {
        val existing = task()
        repository.seed(existing)

        assertThat(service.get(existing.id)).isEqualTo(existing)
        assertThat(repository.insertedTasks).isEmpty()
        assertThat(repository.updatedTasks).isEmpty()
    }

    @Test
    fun `missing tasks cannot be read replaced or enabled`() {
        val missingId = UUID.randomUUID()

        assertThatThrownBy { service.get(missingId) }
            .isInstanceOf(CollectionTaskNotFoundException::class.java)
        assertThatThrownBy { service.replace(missingId, 0, definition()) }
            .isInstanceOf(CollectionTaskNotFoundException::class.java)
        assertThatThrownBy { service.setEnabled(missingId, 0, true) }
            .isInstanceOf(CollectionTaskNotFoundException::class.java)
        assertThat(repository.updatedTasks).isEmpty()
    }

    @Test
    fun `replacement trims the new name and preserves task identity and enabled state`() {
        val original = task().changeEnabled(true)
        repository.seed(original)
        val replacement = definition("  Producer prices  ")

        val revised = service.replace(original.id, original.version, replacement)

        assertThat(revised.id).isEqualTo(original.id)
        assertThat(revised.definition.name).isEqualTo("Producer prices")
        assertThat(revised.createdAt).isEqualTo(original.createdAt)
        assertThat(revised.enabled).isTrue()
        assertThat(revised.version).isEqualTo(original.version + 1)
        assertThat(original.definition.name).isEqualTo("Consumer prices")
        assertThat(repository.updatedTasks).containsExactly(revised)
    }

    @Test
    fun `replacement rejects an unknown source without changing the task`() {
        val original = task()
        repository.seed(original)
        val replacement = definition().copy(sourceId = UUID.randomUUID())

        assertThatThrownBy { service.replace(original.id, 0, replacement) }
            .isInstanceOf(SourceNotFoundException::class.java)
        assertThat(service.get(original.id)).isEqualTo(original)
        assertThat(repository.updatedTasks).isEmpty()
    }

    @Test
    fun `replacement returns the schedule normalized by persistence`() {
        val storedTime = Instant.parse("2026-10-05T00:00:00.123457Z")
        val canonicalRepository = FakeCollectionTaskRepository(storedTime)
        val canonicalService = CollectionTaskService(canonicalRepository, sources, clock)
        val original = canonicalService.create(definition())

        val revised = canonicalService.replace(original.id, 0, definition("Revised"))

        assertThat(revised.definition.schedule.anchorAt).isEqualTo(storedTime)
        assertThat(canonicalService.get(revised.id)).isEqualTo(revised)
    }

    @Test
    fun `enabling and disabling advance versions without mutating the original`() {
        val original = task()
        repository.seed(original)

        val enabled = service.setEnabled(original.id, 0, true)
        val disabled = service.setEnabled(original.id, 1, false)

        assertThat(enabled.enabled).isTrue()
        assertThat(enabled.version).isEqualTo(1)
        assertThat(disabled.enabled).isFalse()
        assertThat(disabled.version).isEqualTo(2)
        assertThat(disabled.definition).isEqualTo(original.definition)
        assertThat(original.enabled).isFalse()
        assertThat(original.version).isZero()
        assertThat(repository.updatedTasks).containsExactly(enabled, disabled)
    }

    @Test
    fun `stale versions cannot replace or enable a task`() {
        val current = task().changeEnabled(true)
        repository.seed(current)

        assertThatThrownBy { service.replace(current.id, 0, definition("Changed")) }
            .isInstanceOf(StaleTaskVersionException::class.java)
        assertThatThrownBy { service.setEnabled(current.id, 0, false) }
            .isInstanceOf(StaleTaskVersionException::class.java)
        assertThat(repository.updatedTasks).isEmpty()
        assertThat(service.get(current.id)).isEqualTo(current)
    }

    @Test
    fun `concurrent modification after the read rejects both update operations`() {
        val original = task()
        repository.seed(original)
        repository.rejectUpdates()

        assertThatThrownBy { service.replace(original.id, 0, definition("Changed")) }
            .isInstanceOf(StaleTaskVersionException::class.java)
        assertThatThrownBy { service.setEnabled(original.id, 0, true) }
            .isInstanceOf(StaleTaskVersionException::class.java)
        assertThat(repository.updatedTasks).isEmpty()
        assertThat(service.get(original.id)).isEqualTo(original)
    }

    @Test
    fun `negative expected versions are invalid configuration`() {
        val original = task()
        repository.seed(original)

        assertThatThrownBy { service.replace(original.id, -1, definition()) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { service.setEnabled(original.id, -1, true) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThat(repository.updatedTasks).isEmpty()
    }

    @Test
    fun `list applies offset and limit`() {
        val first = task()
        val second = task()
        val third = task()
        repository.seed(first, second, third)

        assertThat(service.list(1, 1)).containsExactly(second)
        assertThat(service.list(0, 100)).containsExactly(first, second, third)
        assertThat(service.list(3, 1)).isEmpty()
        assertThat(repository.insertedTasks).isEmpty()
        assertThat(repository.updatedTasks).isEmpty()
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

    private fun task(): CollectionTask = CollectionTask(UUID.randomUUID(), definition(), false, 0, now)

    private fun definition(name: String = "Consumer prices"): TaskDefinition =
        TaskDefinition(name, source.id, CollectionTarget("/observations", mapOf("series" to "CPI")), IntervalSchedule(3600, now))
}
