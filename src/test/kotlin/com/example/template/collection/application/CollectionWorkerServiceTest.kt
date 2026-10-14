package com.example.template.collection.application

import com.example.template.collection.application.port.output.CollectionConnectorException
import com.example.template.collection.application.port.output.CollectionConnectorPort
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.service.CollectionRunService
import com.example.template.collection.application.service.CollectionWorkerService
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.run.Checkpoint
import com.example.template.collection.domain.run.CollectionBatch
import com.example.template.collection.domain.run.CollectionRequest
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.CollectionRunStatus
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

// The run service is real so that each run's outcome follows from the mocked ports. The repository accepts only the
// writes a test prepares, so a run started or closed with any other state ends the call with an exception. The count
// runPending returns covers starting only; how a run was closed shows in the worker's log, which reports a failure only
// after the final write returned the closed run and reports a discarded result when that write returned nothing.
class CollectionWorkerServiceTest {
    private val scheduledFor = Instant.parse("2026-10-05T00:00:00Z")
    private val now = scheduledFor.plusSeconds(30)
    private val lease = Duration.ofMinutes(5)
    private val repository = mockk<CollectionRunRepository>()
    private val connector = mockk<CollectionConnectorPort>()
    private val logs = mutableListOf<String>()
    private val workerLog = Logger.getLogger(CollectionWorkerService::class.java.name)
    private val logCapture =
        object : Handler() {
            override fun publish(record: LogRecord) {
                logs += record.message
            }

            override fun flush() = Unit

            override fun close() = Unit
        }

    @BeforeEach
    fun captureLog() {
        workerLog.addHandler(logCapture)
    }

    @AfterEach
    fun releaseLog() {
        workerLog.removeHandler(logCapture)
    }

    @Test
    fun `a due pending run starts with a lease and succeeds`() {
        val pending = pending()
        every { repository.findPending(now, 10) } returns listOf(pending)
        acceptStart(pending)
        every { connector.collect(any()) } answers { CollectionBatch(firstArg(), emptyList()) }
        acceptClose(pending, CollectionRunStatus.SUCCEEDED)

        assertThat(worker().runPending(10)).isEqualTo(1)

        assertThat(logOf(pending)).isEmpty()
    }

    @Test
    fun `a connector exception fails the run with the reason the connector described, cut to the stored limit`() {
        val described = pending()
        val long = pending()
        every { repository.findPending(now, 10) } returns listOf(described, long)
        acceptStart(described)
        acceptStart(long)
        every { connector.collect(match { it.run.id == described.id }) } throws CollectionConnectorException("source answered HTTP 503")
        every { connector.collect(match { it.run.id == long.id }) } throws CollectionConnectorException("  ${"x".repeat(1500)}")
        acceptClose(described, CollectionRunStatus.FAILED, "source answered HTTP 503")
        acceptClose(long, CollectionRunStatus.FAILED, "x".repeat(1000))

        assertThat(worker().runPending(10)).isEqualTo(2)

        assertThat(logOf(described)).containsExactly(failed(described, "source answered HTTP 503"))
        assertThat(logOf(long)).containsExactly(failed(long, "x".repeat(1000)))
    }

    @Test
    fun `other exceptions fail the run with only their type and never their message`() {
        val invalid = pending()
        val unexpected = pending()
        every { repository.findPending(now, 10) } returns listOf(invalid, unexpected)
        acceptStart(invalid)
        acceptStart(unexpected)
        every { connector.collect(match { it.run.id == invalid.id }) } throws
            InvalidCollectionConfigurationException("response body: token=secret")
        every { connector.collect(match { it.run.id == unexpected.id }) } throws IllegalStateException("token=secret")
        acceptClose(
            invalid,
            CollectionRunStatus.FAILED,
            "connector failed with ${InvalidCollectionConfigurationException::class.java.name}",
        )
        acceptClose(unexpected, CollectionRunStatus.FAILED, "connector failed with java.lang.IllegalStateException")

        assertThat(worker().runPending(10)).isEqualTo(2)

        assertThat(logOf(invalid))
            .containsExactly(failed(invalid, "connector failed with ${InvalidCollectionConfigurationException::class.java.name}"))
        assertThat(logOf(unexpected)).containsExactly(failed(unexpected, "connector failed with java.lang.IllegalStateException"))
        assertThat(logs).noneMatch { "secret" in it }
    }

    @Test
    fun `a batch for a request other than the one sent fails the run and keeps the run the worker started`() {
        val otherRun = pending()
        val earlierStart = pending()
        val otherSnapshot = pending()
        val higherLimit = pending()
        val addedCheckpoint = pending()
        val all = listOf(otherRun, earlierStart, otherSnapshot, higherLimit, addedCheckpoint)
        every { repository.findPending(now, 10) } returns all
        all.forEach { run ->
            acceptStart(run)
            acceptClose(run, CollectionRunStatus.FAILED, "connector returned a batch for a different request")
        }
        every { connector.collect(any()) } answers {
            val request = firstArg<CollectionRequest>()
            val run = request.run
            val returned =
                when (run.id) {
                    otherRun.id -> {
                        CollectionRequest(CollectionRun.pending(UUID.randomUUID(), run.task, scheduledFor).start(now))
                    }

                    earlierStart.id -> {
                        CollectionRequest(CollectionRun.pending(run.id, run.task, scheduledFor).start(scheduledFor))
                    }

                    otherSnapshot.id -> {
                        CollectionRequest(
                            CollectionRun.pending(run.id, run.task.copy(version = 2), scheduledFor).start(now),
                        )
                    }

                    higherLimit.id -> {
                        request.copy(maxRecords = 1000)
                    }

                    else -> {
                        request.copy(checkpoint = Checkpoint(run.task.taskId, run.task.version, "cursor"))
                    }
                }
            CollectionBatch(returned, emptyList())
        }

        assertThat(worker().runPending(10)).isEqualTo(5)

        all.forEach { run ->
            assertThat(logOf(run)).containsExactly(failed(run, "connector returned a batch for a different request"))
        }
    }

    @Test
    fun `a run whose source needs a credential fails without being collected`() {
        val pending = pending(credentialRef = "api-key")
        every { repository.findPending(now, 10) } returns listOf(pending)
        acceptClose(pending, CollectionRunStatus.FAILED, "credential lookup not supported", CollectionRunStatus.PENDING)

        assertThat(worker().runPending(10)).isEqualTo(1)
    }

    @Test
    fun `a run another worker started first is not counted`() {
        val taken = pending()
        val free = pending()
        every { repository.findPending(now, 10) } returns listOf(taken, free)
        every { repository.updateStarted(match { it.id == taken.id }, any()) } returns null
        acceptStart(free)
        every { connector.collect(any()) } answers { CollectionBatch(firstArg(), emptyList()) }
        acceptClose(free, CollectionRunStatus.SUCCEEDED)

        assertThat(worker().runPending(10)).isEqualTo(1)
    }

    @Test
    fun `a failing or rejected run does not stop the runs after it`() {
        val rejected = pending()
        val failing = pending()
        val healthy = pending()
        every { repository.findPending(now, 10) } returns listOf(rejected, failing, healthy)
        every { repository.updateStarted(match { it.id == rejected.id }, any()) } throws
            InvalidCollectionConfigurationException("stored run is corrupted")
        acceptStart(failing)
        acceptStart(healthy)
        every { connector.collect(any()) } answers { CollectionBatch(firstArg(), emptyList()) }
        every { connector.collect(match { it.run.id == failing.id }) } throws CollectionConnectorException("source unavailable")
        acceptClose(failing, CollectionRunStatus.FAILED, "source unavailable")
        acceptClose(healthy, CollectionRunStatus.SUCCEEDED)

        assertThat(worker().runPending(10)).isEqualTo(2)

        assertThat(logOf(rejected))
            .containsExactly("Skipped collection run ${rejected.id} after ${InvalidCollectionConfigurationException::class.java.name}")
        assertThat(logOf(failing)).containsExactly(failed(failing, "source unavailable"))
        assertThat(logOf(healthy)).isEmpty()
    }

    @Test
    fun `a result arriving after another worker closed the run is discarded`() {
        val pending = pending()
        every { repository.findPending(now, 10) } returns listOf(pending)
        acceptStart(pending)
        every { connector.collect(any()) } answers { CollectionBatch(firstArg(), emptyList()) }
        every { repository.update(any(), CollectionRunStatus.RUNNING) } returns null

        assertThat(worker().runPending(10)).isEqualTo(1)

        assertThat(logOf(pending)).containsExactly("Discarded the result of collection run ${pending.id} closed before it finished")
    }

    @Test
    fun `expired leases close their runs as failed and runs closed elsewhere are not counted`() {
        val afterLease = now.plus(lease).plusSeconds(1)
        val expired = pending().start(now)
        val closedElsewhere = pending().start(now)
        every { repository.findExpiredLeases(afterLease, 10) } returns listOf(expired, closedElsewhere)
        every { repository.update(match { it.id == closedElsewhere.id }, CollectionRunStatus.RUNNING) } returns null
        every {
            repository.update(
                match {
                    it.id == expired.id && it.status == CollectionRunStatus.FAILED && it.failureReason == "lease expired" &&
                        it.startedAt == now && it.finishedAt == afterLease
                },
                CollectionRunStatus.RUNNING,
            )
        } answers { firstArg() }

        assertThat(worker(afterLease).expireLeases(10)).isEqualTo(1)
    }

    @Test
    fun `a JVM error or interruption from the connector ends the call`() {
        val pending = pending()
        every { repository.findPending(now, 10) } returns listOf(pending)
        acceptStart(pending)

        every { connector.collect(any()) } throws StackOverflowError()
        assertThatThrownBy { worker().runPending(10) }.isInstanceOf(StackOverflowError::class.java)

        every { connector.collect(any()) } throws InterruptedException()
        assertThatThrownBy { worker().runPending(10) }.isInstanceOf(InterruptedException::class.java)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1001])
    fun `limits outside one through one thousand are rejected`(limit: Int) {
        assertThatThrownBy { worker().runPending(limit) }.isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { worker().expireLeases(limit) }.isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `a lease must last some time`() {
        assertThatThrownBy {
            CollectionWorkerService(CollectionRunService(repository), connector, Clock.fixed(now, ZoneOffset.UTC), Duration.ZERO)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun worker(at: Instant = now): CollectionWorkerService =
        CollectionWorkerService(CollectionRunService(repository), connector, Clock.fixed(at, ZoneOffset.UTC), lease)

    private fun logOf(run: CollectionRun): List<String> = logs.filter { run.id.toString() in it }

    private fun failed(
        run: CollectionRun,
        reason: String,
    ): String = "Collection run ${run.id} failed: $reason"

    private fun acceptStart(pending: CollectionRun) {
        every {
            repository.updateStarted(
                match { it.id == pending.id && it.status == CollectionRunStatus.RUNNING && it.startedAt == now },
                now.plus(lease),
            )
        } answers { firstArg() }
    }

    // Accepts the final write only when it keeps the scheduled and start time and snapshot of the run the worker started.
    private fun acceptClose(
        pending: CollectionRun,
        status: CollectionRunStatus,
        reason: String? = null,
        expectedStatus: CollectionRunStatus = CollectionRunStatus.RUNNING,
    ) {
        every {
            repository.update(
                match {
                    it.id == pending.id && it.status == status && it.failureReason == reason && it.task == pending.task &&
                        it.scheduledFor == pending.scheduledFor && it.startedAt == now && it.finishedAt == now
                },
                expectedStatus,
            )
        } answers { firstArg() }
    }

    private fun pending(credentialRef: String? = null): CollectionRun =
        CollectionRun.pending(UUID.randomUUID(), snapshot(credentialRef), scheduledFor)

    private fun snapshot(credentialRef: String?): TaskSnapshot {
        val sourceId = UUID.randomUUID()
        return TaskSnapshot(
            taskId = UUID.randomUUID(),
            version = 1,
            definition =
                TaskDefinition(
                    "Consumer prices",
                    sourceId,
                    CollectionTarget("/observations"),
                    IntervalSchedule(3600, scheduledFor),
                ),
            source =
                SourceSnapshot(
                    sourceId,
                    SourceDefinition("Economy", SourceKind.HTTP_JSON, "https://example.com", credentialRef),
                    scheduledFor,
                ),
        )
    }
}
