package com.example.template.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
    fun `revising a task advances version while preserving identity and state`() {
        val original = task().changeEnabled(true)
        val revisedDefinition = definition.copy(name = "Daily observations", schedule = IntervalSchedule(86_400, now))

        val revised = original.revise(revisedDefinition)

        assertThat(revised).isEqualTo(original.copy(definition = revisedDefinition, version = 2))
        assertThat(original.definition).isEqualTo(definition)
        assertThat(original.version).isEqualTo(1)
    }

    @Test
    fun `enabling and disabling create new task versions`() {
        val original = task()

        val enabled = original.changeEnabled(true)
        val disabled = enabled.changeEnabled(false)

        assertThat(enabled).isEqualTo(original.copy(enabled = true, version = 1))
        assertThat(disabled).isEqualTo(original.copy(enabled = false, version = 2))
        assertThat(original.enabled).isFalse()
        assertThat(original.version).isZero()
    }

    @Test
    fun `repeating the current enabled value still advances the mutation version`() {
        val original = task()

        assertThat(original.changeEnabled(false)).isEqualTo(original.copy(version = 1))
    }

    @Test
    fun `negative task versions are rejected`() {
        assertThatThrownBy { task().copy(version = -1) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `version exhaustion is rejected without overflow`() {
        val task = task().copy(version = Long.MAX_VALUE)

        assertThatThrownBy { task.changeEnabled(true) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { task.revise(definition) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `blank task names are rejected`(name: String) {
        assertThatThrownBy { definition.copy(name = name) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `task name length uses trimmed content`() {
        assertThat(definition.copy(name = "  ${"a".repeat(120)}  ").name.trim()).hasSize(120)
        assertThatThrownBy { definition.copy(name = "a".repeat(121)) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `unsupported creation timestamps are rejected`() {
        assertThatThrownBy { task().copy(createdAt = Instant.EPOCH.minusNanos(1)) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    private fun task(): CollectionTask = CollectionTask(UUID.randomUUID(), definition, createdAt = now)
}
