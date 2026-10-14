package com.example.template.collection.application

import com.example.template.collection.application.port.output.CheckpointStore
import com.example.template.collection.application.port.output.CollectedRecordStore
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.service.CollectionRunService
import com.example.template.collection.domain.run.Checkpoint
import com.example.template.collection.domain.run.CollectedRecord
import com.example.template.collection.domain.run.CollectionBatch
import com.example.template.collection.domain.run.CollectionRequest
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.CollectionRunStatus
import com.example.template.collection.domain.run.RecordIdentity
import com.example.template.collection.domain.run.RecordProvenance
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

// Only returned values and exceptions are asserted. What the stores keep is the adapter tests' concern.
class CollectionRunServiceTest {
    private val scheduledFor = Instant.parse("2026-10-05T00:00:00Z")
    private val startedAt = scheduledFor.plusSeconds(10)
    private val finishedAt = scheduledFor.plusSeconds(40)
    private val repository = mockk<CollectionRunRepository>()
    private val records = mockk<CollectedRecordStore>()
    private val checkpoints = mockk<CheckpointStore>()
    private val service = CollectionRunService(repository, records, checkpoints)
    private val running = CollectionRun.pending(UUID.randomUUID(), snapshot(), scheduledFor).start(startedAt)

    @Test
    fun `a request carries the stored checkpoint of the same task version`() {
        val stored = Checkpoint(running.task.taskId, running.task.version, "cursor-1")
        every { checkpoints.find(running.task.taskId) } returns stored

        val request = service.requestFor(running)

        assertThat(request).isEqualTo(CollectionRequest(running, stored))
    }

    @Test
    fun `a request starts fresh when there is no checkpoint or it belongs to another task version`() {
        every { checkpoints.find(running.task.taskId) } returns null
        assertThat(service.requestFor(running).checkpoint).isNull()

        every { checkpoints.find(running.task.taskId) } returns Checkpoint(running.task.taskId, running.task.version - 1, "old")
        assertThat(service.requestFor(running).checkpoint).isNull()
    }

    @Test
    fun `a batch with records and a candidate checkpoint closes the run as succeeded at the finish time`() {
        val record = record()
        val next = Checkpoint(running.task.taskId, running.task.version, "cursor-2")
        val batch = CollectionBatch(CollectionRequest(running), listOf(record), next)
        every {
            repository.update(match { it.id == running.id && it.status == CollectionRunStatus.SUCCEEDED }, CollectionRunStatus.RUNNING)
        } answers
            { firstArg() }
        every { records.saveNew(listOf(record)) } returns 1
        every { checkpoints.save(next, running.id, finishedAt) } returns Unit

        val closed = service.complete(running, batch, finishedAt)

        assertThat(closed?.status).isEqualTo(CollectionRunStatus.SUCCEEDED)
        assertThat(closed?.finishedAt).isEqualTo(finishedAt)
    }

    @Test
    fun `a batch without a candidate checkpoint closes the run as succeeded`() {
        val record = record()
        val batch = CollectionBatch(CollectionRequest(running), listOf(record))
        every { repository.update(any(), CollectionRunStatus.RUNNING) } answers { firstArg() }
        every { records.saveNew(listOf(record)) } returns 0

        assertThat(service.complete(running, batch, finishedAt)?.status).isEqualTo(CollectionRunStatus.SUCCEEDED)
    }

    @Test
    fun `a result for a run already closed by its lease expiry completes as null`() {
        val batch =
            CollectionBatch(CollectionRequest(running), listOf(record()), Checkpoint(running.task.taskId, running.task.version, "c"))
        every { repository.update(any(), CollectionRunStatus.RUNNING) } returns null

        assertThat(service.complete(running, batch, finishedAt)).isNull()
    }

    @Test
    fun `a record store failure ends the completion with that failure`() {
        val batch = CollectionBatch(CollectionRequest(running), listOf(record()))
        every { repository.update(any(), CollectionRunStatus.RUNNING) } answers { firstArg() }
        every { records.saveNew(any()) } throws IllegalStateException("database unavailable")

        assertThatThrownBy { service.complete(running, batch, finishedAt) }.hasMessage("database unavailable")
    }

    @Test
    fun `a batch of another run cannot complete the run`() {
        val other = CollectionRun.pending(UUID.randomUUID(), running.task, scheduledFor).start(startedAt)

        assertThatThrownBy { service.complete(running, CollectionBatch(CollectionRequest(other), emptyList()), finishedAt) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun record() =
        CollectedRecord(
            identity = RecordIdentity("/observations", "a".repeat(64)),
            schemaVersion = "http-json/1",
            payload = "{}",
            provenance = RecordProvenance(running.id, running.task.source.id, finishedAt),
        )

    private fun snapshot(): TaskSnapshot {
        val sourceId = UUID.randomUUID()
        return TaskSnapshot(
            taskId = UUID.randomUUID(),
            version = 1,
            definition =
                TaskDefinition("Prices", sourceId, CollectionTarget("/observations"), IntervalSchedule(3600, scheduledFor)),
            source =
                SourceSnapshot(
                    sourceId,
                    SourceDefinition("Economy", SourceKind.HTTP_JSON, "https://example.com", null),
                    scheduledFor,
                ),
        )
    }
}
