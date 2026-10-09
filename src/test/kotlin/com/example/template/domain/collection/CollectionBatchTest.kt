package com.example.template.domain.collection

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
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class CollectionBatchTest {
    private val startedAt = Instant.parse("2026-10-05T00:00:00Z")
    private val source = Source(UUID.randomUUID(), SourceDefinition("경제 지표", SourceKind.HTTP_JSON, "https://example.com"), startedAt)
    private val definition = TaskDefinition("물가 수집", source.id, CollectionTarget("/prices"), IntervalSchedule(60, startedAt))
    private val task = CollectionTask(UUID.randomUUID(), definition, createdAt = startedAt)
    private val pending = CollectionRun.pending(UUID.randomUUID(), TaskSnapshot.from(task, source), startedAt)
    private val running = pending.start(startedAt)

    @Test
    fun `빈 배치도 같은 작업의 다음 체크포인트를 가질 수 있다`() {
        val previousCheckpoint = Checkpoint(task.id, task.version, "page-1")
        val checkpoint = Checkpoint(task.id, task.version, "page-2")
        val request = CollectionRequest(running, previousCheckpoint)

        val batch = CollectionBatch(request, emptyList(), checkpoint)

        assertThat(batch.records).isEmpty()
        assertThat(batch.nextCheckpoint).isEqualTo(checkpoint)
        assertThat(request.checkpoint).isEqualTo(previousCheckpoint)
    }

    @Test
    fun `배치는 전달받은 변경 가능한 리스트와 분리된다`() {
        val collected = record()
        val records = mutableListOf(collected)

        val batch = CollectionBatch(CollectionRequest(running), records)
        records.clear()
        records.add(record(payload = "changed"))

        assertThat(batch.records).containsExactly(collected)
    }

    @Test
    fun `요청한 최대 건수까지 허용하고 한 건이라도 넘으면 거부한다`() {
        val request = CollectionRequest(running, maxRecords = 2)
        val collected = record()

        assertThat(CollectionBatch(request, listOf(collected, record(key = "prices-2"))).records).hasSize(2)
        assertThatThrownBy { CollectionBatch(request, List(3) { record(key = "prices-$it") }) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `개별 크기를 지켜도 배치 전체가 400만 문자를 넘으면 거부한다`() {
        val request = CollectionRequest(running)
        val records = List(4) { record(key = "prices-$it", payload = "a".repeat(1_000_000)) }

        assertThat(CollectionBatch(request, records).records).hasSize(4)
        assertThatThrownBy { CollectionBatch(request, records + record(key = "extra", payload = "a")) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `실행 중인 요청만 커넥터에 전달할 수 있다`() {
        val invalidRuns = listOf(pending, running.succeed(startedAt), running.fail(startedAt, "failed"))

        invalidRuns.forEach { run ->
            assertThatThrownBy { CollectionRequest(run) }
                .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1001])
    fun `요청 건수는 1부터 1000 사이여야 한다`(maxRecords: Int) {
        assertThatThrownBy { CollectionRequest(running, maxRecords = maxRecords) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 1000])
    fun `요청 건수의 양쪽 경계는 허용한다`(maxRecords: Int) {
        assertThat(CollectionRequest(running, maxRecords = maxRecords).maxRecords).isEqualTo(maxRecords)
    }

    @Test
    fun `다른 작업이나 설정 버전의 체크포인트로 재개하거나 전진할 수 없다`() {
        val invalidCheckpoints = listOf(Checkpoint(UUID.randomUUID(), task.version, "next"), Checkpoint(task.id, task.version + 1, "next"))

        invalidCheckpoints.forEach { checkpoint ->
            assertThatThrownBy { CollectionRequest(running, checkpoint) }
                .isInstanceOf(InvalidCollectionConfigurationException::class.java)
            assertThatThrownBy { CollectionBatch(CollectionRequest(running), emptyList(), checkpoint) }
                .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        }
    }

    @Test
    fun `다른 실행이나 데이터 출처의 레코드는 섞을 수 없다`() {
        val wrongRun = RecordProvenance(UUID.randomUUID(), source.id, startedAt)
        val wrongSource = RecordProvenance(running.id, UUID.randomUUID(), startedAt)

        listOf(wrongRun, wrongSource).forEach { provenance ->
            val collected = CollectedRecord(RecordIdentity("prices-1", "a".repeat(64)), "v1", "{}", provenance)
            assertThatThrownBy { CollectionBatch(CollectionRequest(running), listOf(collected)) }
                .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        }
    }

    @Test
    fun `실행 시작보다 앞선 수집 시각은 거부하고 같은 시각은 허용한다`() {
        val request = CollectionRequest(running)

        assertThat(CollectionBatch(request, listOf(record())).records).hasSize(1)
        assertThatThrownBy { CollectionBatch(request, listOf(record(collectedAt = startedAt.minusNanos(1)))) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `체크포인트는 빈 값과 크기 초과를 거부한다`() {
        assertThat(Checkpoint(task.id, task.version, "a".repeat(16_384)).value).hasSize(16_384)
        assertThatThrownBy { Checkpoint(task.id, task.version, " ") }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { Checkpoint(task.id, task.version, "a".repeat(16_385)) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    private fun record(
        key: String = "prices-1",
        payload: String = "{}",
        collectedAt: Instant = startedAt,
    ): CollectedRecord =
        CollectedRecord(RecordIdentity(key, "a".repeat(64)), "v1", payload, RecordProvenance(running.id, source.id, collectedAt))
}
