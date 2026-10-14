package com.example.template.collection.application

import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.application.service.CollectionSlotService
import com.example.template.collection.domain.run.CollectionRunStatus
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import com.example.template.collection.domain.task.TaskRevision
import com.example.template.collection.domain.task.TaskTimeline
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CollectionSlotServiceTest {
    private val anchor = Instant.parse("2026-10-05T00:00:00Z")
    private val dueSlot = anchor.plusSeconds(3600)
    private val now = anchor.plusSeconds(3 * 3600 + 1800)
    private val tasks = mockk<CollectionTaskRepository>()
    private val sources = mockk<SourceRepository>()
    private val runs = mockk<CollectionRunRepository>()
    private val service = CollectionSlotService(tasks, sources, runs)
    private val source =
        Source.create(UUID.randomUUID(), SourceDefinition("Economy", SourceKind.HTTP_JSON, "https://example.com", "api-key"), anchor)
    private val definition =
        TaskDefinition(
            "Consumer prices",
            source.id,
            CollectionTarget("/observations", mapOf("series" to "CPI")),
            IntervalSchedule(3600, anchor),
        )
    private val dueTask = CollectionTask.restore(UUID.randomUUID(), definition, TaskRevision(true, 4), TaskTimeline(anchor, dueSlot))

    @Test
    fun `claiming a late slot creates one pending run for the stored slot with the task and source snapshot`() {
        every { sources.findById(source.id) } returns source
        every { tasks.advanceNextRun(any(), dueSlot) } returns true
        every { runs.existsForSlot(dueTask.id, dueSlot) } returns false
        every { runs.insert(any()) } answers { firstArg() }

        val run = requireNotNull(service.claim(dueTask, now))

        assertThat(run.status).isEqualTo(CollectionRunStatus.PENDING)
        assertThat(run.scheduledFor).isEqualTo(dueSlot)
        assertThat(run.startedAt).isNull()
        assertThat(run.task.taskId).isEqualTo(dueTask.id)
        assertThat(run.task.version).isEqualTo(4)
        assertThat(run.task.definition).isEqualTo(definition)
        assertThat(run.task.source).isEqualTo(SourceSnapshot(source.id, source.definition, source.createdAt))
    }

    @Test
    fun `claiming creates no run when another scheduler or an operator changed the task first`() {
        every { sources.findById(source.id) } returns source
        every { tasks.advanceNextRun(any(), dueSlot) } returns false

        assertThat(service.claim(dueTask, now)).isNull()
    }

    @Test
    fun `claiming creates no run when the slot already has one`() {
        every { sources.findById(source.id) } returns source
        every { tasks.advanceNextRun(any(), dueSlot) } returns true
        every { runs.existsForSlot(dueTask.id, dueSlot) } returns true

        assertThat(service.claim(dueTask, now)).isNull()
    }

    @Test
    fun `claiming fails for a missing source`() {
        every { sources.findById(source.id) } returns null

        assertThatThrownBy { service.claim(dueTask, now) }
            .isInstanceOf(SourceNotFoundException::class.java)
    }

    @Test
    fun `claiming rejects a task whose slot is not due`() {
        every { sources.findById(source.id) } returns source

        assertThatThrownBy { service.claim(dueTask, dueSlot.minusNanos(1)) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `initializing gives an unscheduled task the next slot after now without changing its version`() {
        val unscheduled = CollectionTask.restore(UUID.randomUUID(), definition, TaskRevision(true, 4), TaskTimeline(anchor))
        every { tasks.advanceNextRun(any(), null) } returns true

        val initialized = requireNotNull(service.initialize(unscheduled, now))

        assertThat(initialized.nextRunAt).isEqualTo(anchor.plusSeconds(4 * 3600))
        assertThat(initialized.version).isEqualTo(4)
    }

    @Test
    fun `initializing returns nothing when the stored task changed first`() {
        val unscheduled = CollectionTask.restore(UUID.randomUUID(), definition, TaskRevision(true, 4), TaskTimeline(anchor))
        every { tasks.advanceNextRun(any(), null) } returns false

        assertThat(service.initialize(unscheduled, now)).isNull()
    }
}
