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
    fun `a new task is disabled at version zero without a next run`() {
        val task = task()

        assertThat(task.enabled).isFalse()
        assertThat(task.version).isZero()
        assertThat(task.nextRunAt).isNull()
    }

    @Test
    fun `creation and revision normalize names`() {
        val original = CollectionTask.create(UUID.randomUUID(), definition.copy(name = "  Original  "), now)
        val revised = original.revise(definition.copy(name = "  Revised  "), now).changeEnabled(true, now)

        assertThat(original.definition.name).isEqualTo("Original")
        assertThat(revised.definition.name).isEqualTo("Revised")
        assertThat(revised.enabled).isTrue()
        assertThat(revised.version).isEqualTo(2)
        assertThat(revised.id).isEqualTo(original.id)
    }

    @Test
    fun `revising an enabled task advances version and recomputes the next run from the new schedule`() {
        val original = task().changeEnabled(true, now)
        val revisedAt = now.plusSeconds(90)
        val revisedDefinition = definition.copy(name = "Daily observations", schedule = IntervalSchedule(86_400, now))

        val revised = original.revise(revisedDefinition, revisedAt)

        assertThat(revised).isEqualTo(
            CollectionTask.restore(
                original.id,
                revisedDefinition,
                TaskRevision(true, 2),
                TaskTimeline(original.createdAt, now.plusSeconds(86_400)),
            ),
        )
        assertThat(original.definition).isEqualTo(definition)
        assertThat(original.version).isEqualTo(1)
        assertThat(original.nextRunAt).isEqualTo(now.plusSeconds(3600))
    }

    @Test
    fun `revising a disabled task keeps it without a next run`() {
        val revised = task().revise(definition.copy(name = "Renamed"), now.plusSeconds(90))

        assertThat(revised.nextRunAt).isNull()
        assertThat(revised.version).isEqualTo(1)
    }

    @Test
    fun `enabling schedules the next slot after now and disabling clears it with new versions`() {
        val original = task()
        val enabledAt = now.plusSeconds(3600)

        val enabled = original.changeEnabled(true, enabledAt)
        val disabled = enabled.changeEnabled(false, enabledAt.plusSeconds(1))

        assertThat(enabled).isEqualTo(
            CollectionTask.restore(
                original.id,
                original.definition,
                TaskRevision(true, 1),
                TaskTimeline(original.createdAt, now.plusSeconds(7200)),
            ),
        )
        assertThat(disabled).isEqualTo(
            CollectionTask.restore(original.id, original.definition, TaskRevision(false, 2), TaskTimeline(original.createdAt)),
        )
        assertThat(original.enabled).isFalse()
        assertThat(original.version).isZero()
    }

    @Test
    fun `repeating the current enabled value still advances the mutation version`() {
        val original = task()

        assertThat(original.changeEnabled(false, now))
            .isEqualTo(CollectionTask.restore(original.id, original.definition, TaskRevision(false, 1), TaskTimeline(original.createdAt)))
    }

    @Test
    fun `advancing a due slot keeps version and moves to the first slot after now without replaying missed slots`() {
        val enabled = task().changeEnabled(true, now)
        val late = now.plusSeconds(5 * 3600 + 30)

        val advanced = enabled.advanceSlot(late)

        assertThat(advanced.nextRunAt).isEqualTo(now.plusSeconds(6 * 3600))
        assertThat(advanced.version).isEqualTo(enabled.version)
        assertThat(advanced.enabled).isTrue()
        assertThat(advanced.definition).isEqualTo(enabled.definition)
        assertThat(enabled.nextRunAt).isEqualTo(now.plusSeconds(3600))
    }

    @Test
    fun `advancing exactly at the due slot moves to the following slot`() {
        val enabled = task().changeEnabled(true, now)

        assertThat(enabled.advanceSlot(now.plusSeconds(3600)).nextRunAt).isEqualTo(now.plusSeconds(7200))
    }

    @Test
    fun `only an enabled task with a due slot can advance`() {
        val enabled = task().changeEnabled(true, now)

        assertThatThrownBy { enabled.advanceSlot(now.plusSeconds(3599)) }.isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { task().advanceSlot(now.plusSeconds(3600)) }.isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { unscheduled().advanceSlot(now.plusSeconds(3600)) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `initializing an enabled task without a next run sets the next slot after now without a new version`() {
        val unscheduled = unscheduled()

        val initialized = unscheduled.initializeNextRun(now.plusSeconds(3600))

        assertThat(initialized.nextRunAt).isEqualTo(now.plusSeconds(7200))
        assertThat(initialized.version).isEqualTo(unscheduled.version)
        assertThatThrownBy { initialized.initializeNextRun(now) }.isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { task().initializeNextRun(now) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `a disabled task cannot be restored with a next run`() {
        assertThatThrownBy {
            CollectionTask.restore(UUID.randomUUID(), definition, TaskRevision(false, 1), TaskTimeline(now, now.plusSeconds(3600)))
        }.isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `negative task versions are rejected`() {
        assertThatThrownBy { TaskRevision(false, -1) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `version exhaustion is rejected without overflow`() {
        val original = task()
        val task =
            CollectionTask.restore(original.id, original.definition, TaskRevision(false, Long.MAX_VALUE), TaskTimeline(original.createdAt))

        assertThatThrownBy { task.changeEnabled(true, now) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { task.revise(definition, now) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `unsupported creation and next run timestamps are rejected`() {
        assertThatThrownBy { CollectionTask.create(UUID.randomUUID(), definition, Instant.EPOCH.minusNanos(1)) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { TaskTimeline(now, Instant.parse("+10000-01-01T00:00:00Z")) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    private fun task(): CollectionTask = CollectionTask.create(UUID.randomUUID(), definition, createdAt = now)

    private fun unscheduled(): CollectionTask =
        CollectionTask.restore(UUID.randomUUID(), definition, TaskRevision(true, 3), TaskTimeline(now))
}
