package com.example.template.collection.application

import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionRunSlotTakenException
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.application.service.CollectionSchedulingService
import com.example.template.collection.application.service.CollectionSlotService
import com.example.template.collection.domain.InvalidCollectionConfigurationException
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// The per-task service is real so that the outcome of each task follows from the mocked ports.
class CollectionSchedulingServiceTest {
    private val anchor = Instant.parse("2026-10-05T00:00:00Z")
    private val now = anchor.plusSeconds(3600)
    private val tasks = mockk<CollectionTaskRepository>()
    private val sources = mockk<SourceRepository>()
    private val runs = mockk<CollectionRunRepository>()
    private val service =
        CollectionSchedulingService(CollectionSlotService(tasks, sources, runs), Clock.fixed(now, ZoneOffset.UTC))
    private val source = Source.create(UUID.randomUUID(), SourceDefinition("Economy", SourceKind.HTTP_JSON, "https://example.com"), anchor)

    @Test
    fun `due tasks each create a pending run and unscheduled tasks are only initialized`() {
        val first = task(now)
        val second = task(now.minusSeconds(1800))
        every { tasks.findUnscheduled(10) } returns listOf(task(null))
        every { tasks.findDue(now, 10) } returns listOf(first, second)
        every { tasks.advanceNextRun(any(), any()) } returns true
        every { sources.findById(source.id) } returns source
        every { runs.existsForSlot(any(), any()) } returns false
        every { runs.insert(any()) } answers { firstArg() }

        assertThat(service.scheduleDue(10)).isEqualTo(2)
    }

    @Test
    fun `an unscheduled task never creates a run in the call that initializes it`() {
        every { tasks.findUnscheduled(10) } returns listOf(task(null))
        every { tasks.advanceNextRun(any(), null) } returns true
        every { tasks.findDue(now, 10) } returns emptyList()

        assertThat(service.scheduleDue(10)).isZero()
    }

    @Test
    fun `failing or contended tasks are skipped without stopping the remaining tasks`() {
        val takenSlot = task(now)
        val claimedElsewhere = task(now)
        val missingSource = task(now, sourceId = UUID.randomUUID())
        val healthy = task(now)
        every { tasks.findUnscheduled(10) } returns emptyList()
        every { tasks.findDue(now, 10) } returns listOf(takenSlot, claimedElsewhere, missingSource, healthy)
        every { sources.findById(source.id) } returns source
        every { sources.findById(missingSource.definition.sourceId) } returns null
        every { tasks.advanceNextRun(any(), now) } returns true
        every { tasks.advanceNextRun(match { it.id == claimedElsewhere.id }, now) } returns false
        every { runs.existsForSlot(any(), now) } returns false
        every { runs.insert(any()) } answers { firstArg() }
        every { runs.insert(match { it.task.taskId == takenSlot.id }) } throws
            CollectionRunSlotTakenException(takenSlot.id, now, SQLException("duplicate"))

        assertThat(service.scheduleDue(10)).isEqualTo(1)
    }

    @Test
    fun `a task whose next slot cannot be computed does not stop the remaining work`() {
        val latest = Instant.parse("9999-12-31T23:00:00Z")
        val exhausted = task(null)
        val due =
            CollectionTask.restore(
                UUID.randomUUID(),
                definition(source.id).copy(schedule = IntervalSchedule(60, latest.minusSeconds(3600))),
                TaskRevision(true, 1),
                TaskTimeline(anchor, latest),
            )
        val lateService = CollectionSchedulingService(CollectionSlotService(tasks, sources, runs), Clock.fixed(latest, ZoneOffset.UTC))
        every { tasks.findUnscheduled(10) } returns listOf(exhausted)
        every { tasks.findDue(latest, 10) } returns listOf(due)
        every { tasks.advanceNextRun(any(), latest) } returns true
        every { sources.findById(source.id) } returns source
        every { runs.existsForSlot(due.id, latest) } returns false
        every { runs.insert(any()) } answers { firstArg() }

        assertThat(lateService.scheduleDue(10)).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1001])
    fun `limits outside one through one thousand are rejected`(limit: Int) {
        assertThatThrownBy { service.scheduleDue(limit) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    private fun task(
        nextRunAt: Instant?,
        sourceId: UUID = source.id,
    ): CollectionTask =
        CollectionTask.restore(UUID.randomUUID(), definition(sourceId), TaskRevision(true, 1), TaskTimeline(anchor, nextRunAt))

    private fun definition(sourceId: UUID): TaskDefinition =
        TaskDefinition("Consumer prices", sourceId, CollectionTarget("/observations"), IntervalSchedule(3600, anchor))
}
