package com.example.template.collection.application

import com.example.template.collection.application.port.input.StaleTaskVersionException
import com.example.template.collection.application.port.output.CollectionConnectorPort
import com.example.template.collection.application.port.output.CollectionControlStore
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.application.service.CollectionOperationStateService
import com.example.template.collection.application.service.CollectionOperationsService
import com.example.template.collection.domain.run.CollectionBatch
import com.example.template.collection.domain.run.CollectionRun
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CollectionOperationsServiceTest {
    private val now = Instant.parse("2026-11-16T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val tasks = mockk<CollectionTaskRepository>()
    private val sources = mockk<SourceRepository>()
    private val runs = mockk<CollectionRunRepository>()
    private val control = mockk<CollectionControlStore>()
    private val connector = mockk<CollectionConnectorPort>()
    private val state = CollectionOperationStateService(tasks, sources, runs, control, clock)
    private val service = CollectionOperationsService(state, connector, clock)
    private val source = Source.create(UUID.randomUUID(), SourceDefinition("Public", SourceKind.HTTP_JSON, "https://example.com"), now)
    private val task =
        CollectionTask.create(
            UUID.randomUUID(),
            TaskDefinition(
                "Items",
                source.id,
                CollectionTarget("/items"),
                IntervalSchedule(60, now),
            ),
            now,
        )

    @Test
    fun `preview returns bounded sample values and leaves the plan snapshot intact`() {
        every { tasks.findById(task.id) } returns task
        every { sources.findById(source.id) } returns source
        every { connector.collect(any()) } answers { CollectionBatch(firstArg(), emptyList()) }
        val result = service.preview(task.id)
        assertThat(result.records).isEmpty()
        assertThat(result.recordUnit).isEqualTo("whole JSON response")
        assertThat(task.version).isZero()
        assertThat(task.nextRunAt).isNull()
        assertThat(service.validate(task.id).executable).isTrue()
    }

    @Test
    fun `a new manual request queues a pending run without moving the schedule`() {
        every { tasks.findById(task.id) } returns task
        every { sources.findById(source.id) } returns source
        every { control.lockTask(task.id) } returns Unit
        every { control.findRequest(task.id, "new-request") } returns null
        every { runs.existsForSlot(task.id, now) } returns false
        every { runs.insert(any()) } answers { firstArg() }
        every { control.saveRequest(task.id, "new-request", any()) } returns Unit
        val run = service.runNow(task.id, 0, "new-request")
        assertThat(run.status.name).isEqualTo("PENDING")
        assertThat(run.task.taskId).isEqualTo(task.id)
        assertThat(run.scheduledFor).isEqualTo(now)
        assertThat(task.nextRunAt).isNull()
    }

    @Test
    fun `same manual request returns its existing run and changed version conflicts`() {
        every { tasks.findById(task.id) } returns task
        every { sources.findById(source.id) } returns source
        val run = CollectionRun.pending(UUID.randomUUID(), state.snapshot(task.id), now)
        every { control.lockTask(task.id) } returns Unit
        every { control.findRequest(task.id, "request-1") } returns run.id
        every { runs.findById(run.id) } returns run
        assertThat(service.runNow(task.id, 0, "request-1").id).isEqualTo(run.id)
        assertThatThrownBy { service.runNow(task.id, 1, "request-1") }.isInstanceOf(StaleTaskVersionException::class.java)
    }
}
