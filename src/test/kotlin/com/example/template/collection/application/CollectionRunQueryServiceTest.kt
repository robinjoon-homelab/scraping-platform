package com.example.template.collection.application

import com.example.template.collection.application.model.CollectedRecordSummary
import com.example.template.collection.application.model.StoredCollectedRecord
import com.example.template.collection.application.port.input.CollectedRecordNotFoundException
import com.example.template.collection.application.port.input.CollectionRunNotFoundException
import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.application.port.output.CollectedRecordStore
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.application.service.CollectionRunQueryService
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.run.CollectedRecord
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.RecordIdentity
import com.example.template.collection.domain.run.RecordProvenance
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
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
import java.time.Instant
import java.util.UUID

class CollectionRunQueryServiceTest {
    private val at = Instant.parse("2026-10-05T00:00:00Z")
    private val tasks = mockk<CollectionTaskRepository>()
    private val runs = mockk<CollectionRunRepository>()
    private val records = mockk<CollectedRecordStore>()
    private val service = CollectionRunQueryService(tasks, runs, records)
    private val missing: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val sourceId = UUID.randomUUID()
    private val definition =
        TaskDefinition("Prices", sourceId, CollectionTarget("/prices"), IntervalSchedule(3600, Instant.parse("2026-01-01T00:00:00Z")))
    private val task = CollectionTask.create(UUID.randomUUID(), definition, at)
    private val run = CollectionRun.pending(UUID.randomUUID(), snapshot(), at)

    @Test
    fun `task runs are the page the repository returns`() {
        every { tasks.findById(task.id) } returns task
        every { runs.findByTask(task.id, 5, 10) } returns listOf(run)

        assertThat(service.listRuns(task.id, 5, 10)).containsExactly(run)
    }

    @Test
    fun `runs of a missing task and an invalid page are rejected`() {
        every { tasks.findById(task.id) } returns null

        assertThatThrownBy { service.listRuns(task.id, 0, 10) }.isInstanceOf(CollectionTaskNotFoundException::class.java)
        assertThatThrownBy { service.listRuns(task.id, -1, 10) }.isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { service.listRuns(task.id, 0, 101) }.isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `a run is returned by id or reported missing`() {
        every { runs.findById(run.id) } returns run
        every { runs.findById(missing) } returns null

        assertThat(service.getRun(run.id)).isEqualTo(run)
        assertThatThrownBy { service.getRun(missing) }.isInstanceOf(CollectionRunNotFoundException::class.java)
    }

    @Test
    fun `run records are the summaries the store returns`() {
        val summary = CollectedRecordSummary(UUID.randomUUID(), RecordIdentity("/prices", HASH), "http-json/1", at, null, 11)
        every { runs.findById(run.id) } returns run
        every { records.findFirstObservedBy(run.id, 0, 50) } returns listOf(summary)

        assertThat(service.listRecords(run.id, 0, 50)).containsExactly(summary)
    }

    @Test
    fun `records of a missing run and an invalid page are rejected`() {
        every { runs.findById(missing) } returns null

        assertThatThrownBy { service.listRecords(missing, 0, 50) }.isInstanceOf(CollectionRunNotFoundException::class.java)
        assertThatThrownBy { service.listRecords(run.id, 0, 0) }.isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `a record is returned by id or reported missing`() {
        val stored =
            StoredCollectedRecord(
                UUID.randomUUID(),
                CollectedRecord(RecordIdentity("/prices", HASH), "http-json/1", "{}", RecordProvenance(run.id, sourceId, at)),
            )
        every { records.findById(stored.id) } returns stored
        every { records.findById(missing) } returns null

        assertThat(service.getRecord(stored.id)).isEqualTo(stored)
        assertThatThrownBy { service.getRecord(missing) }.isInstanceOf(CollectedRecordNotFoundException::class.java)
    }

    private fun snapshot() =
        TaskSnapshot(
            taskId = task.id,
            version = 0,
            definition = definition,
            source = SourceSnapshot(sourceId, SourceDefinition("Economy", SourceKind.HTTP_JSON, "https://example.com"), at),
        )

    private companion object {
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
