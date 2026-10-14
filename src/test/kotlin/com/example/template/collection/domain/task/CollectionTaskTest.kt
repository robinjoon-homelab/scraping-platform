package com.example.template.collection.domain.task

import com.example.template.collection.domain.InvalidCollectionConfigurationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CollectionTaskTest {
    private val now = Instant.parse("2026-10-05T00:00:00Z")
    private val definition =
        TaskDefinition(
            name = "Economy observations",
            sourceId = UUID.randomUUID(),
            target = CollectionTarget("/observations", mapOf("series" to "CPI")),
            schedule = IntervalSchedule(3600, now),
        )

    @Test
    fun `a new task is disabled at version zero`() {
        val task = task()

        assertThat(task.enabled).isFalse()
        assertThat(task.version).isZero()
    }

    @Test
    fun `creation and revision normalize names`() {
        val original = CollectionTask.create(UUID.randomUUID(), definition.copy(name = "  Original  "), now)
        val revised = original.revise(definition.copy(name = "  Revised  ")).changeEnabled(true)

        assertThat(original.definition.name).isEqualTo("Original")
        assertThat(revised.definition.name).isEqualTo("Revised")
        assertThat(revised.enabled).isTrue()
        assertThat(revised.version).isEqualTo(2)
        assertThat(revised.id).isEqualTo(original.id)
    }

    @Test
    fun `revising a task advances version while preserving identity and state`() {
        val original = task().changeEnabled(true)
        val revisedDefinition = definition.copy(name = "Daily observations", schedule = IntervalSchedule(86_400, now))

        val revised = original.revise(revisedDefinition)

        assertThat(revised).isEqualTo(CollectionTask.restore(original.id, revisedDefinition, TaskRevision(true, 2), original.createdAt))
        assertThat(original.definition).isEqualTo(definition)
        assertThat(original.version).isEqualTo(1)
    }

    @Test
    fun `enabling and disabling create new task versions`() {
        val original = task()

        val enabled = original.changeEnabled(true)
        val disabled = enabled.changeEnabled(false)

        assertThat(enabled).isEqualTo(CollectionTask.restore(original.id, original.definition, TaskRevision(true, 1), original.createdAt))
        assertThat(disabled).isEqualTo(CollectionTask.restore(original.id, original.definition, TaskRevision(false, 2), original.createdAt))
        assertThat(original.enabled).isFalse()
        assertThat(original.version).isZero()
    }

    @Test
    fun `repeating the current enabled value still advances the mutation version`() {
        val original = task()

        assertThat(original.changeEnabled(false))
            .isEqualTo(CollectionTask.restore(original.id, original.definition, TaskRevision(false, 1), original.createdAt))
    }

    @Test
    fun `negative task versions are rejected`() {
        assertThatThrownBy { TaskRevision(false, -1) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `version exhaustion is rejected without overflow`() {
        val original = task()
        val task = CollectionTask.restore(original.id, original.definition, TaskRevision(false, Long.MAX_VALUE), original.createdAt)

        assertThatThrownBy { task.changeEnabled(true) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { task.revise(definition) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `unsupported creation timestamps are rejected`() {
        assertThatThrownBy { CollectionTask.create(UUID.randomUUID(), definition, Instant.EPOCH.minusNanos(1)) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    private fun task(): CollectionTask = CollectionTask.create(UUID.randomUUID(), definition, createdAt = now)
}
