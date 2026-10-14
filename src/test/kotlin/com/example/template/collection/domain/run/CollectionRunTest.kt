package com.example.template.collection.domain.run

import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class CollectionRunTest {
    private val scheduledAt = Instant.parse("2026-10-05T00:00:00Z")
    private val source =
        Source.create(
            UUID.randomUUID(),
            SourceDefinition("경제 지표", SourceKind.HTTP_JSON, "https://example.com"),
            scheduledAt,
        )
    private val definition = TaskDefinition("물가 수집", source.id, CollectionTarget("/prices"), IntervalSchedule(60, scheduledAt))
    private val task = CollectionTask.create(UUID.randomUUID(), definition, createdAt = scheduledAt)

    @Test
    fun `실행은 설정 스냅샷을 유지하며 원본을 변경하지 않고 성공한다`() {
        val pending = pending()
        val startedAt = scheduledAt.plusSeconds(1)
        val finishedAt = startedAt.plusSeconds(1)

        val running = pending.start(startedAt)
        val succeeded = running.succeed(finishedAt)
        val revisedTask = task.revise(definition.copy(name = "수정한 작업"), scheduledAt)

        assertThat(pending.status).isEqualTo(CollectionRunStatus.PENDING)
        assertThat(pending.startedAt).isNull()
        assertThat(pending.finishedAt).isNull()
        assertThat(pending.failureReason).isNull()
        assertThat(running.status).isEqualTo(CollectionRunStatus.RUNNING)
        assertThat(running.finishedAt).isNull()
        assertThat(succeeded.id).isEqualTo(pending.id)
        assertThat(succeeded.status).isEqualTo(CollectionRunStatus.SUCCEEDED)
        assertThat(succeeded.startedAt).isEqualTo(startedAt)
        assertThat(succeeded.finishedAt).isEqualTo(finishedAt)
        assertThat(succeeded.failureReason).isNull()
        assertThat(succeeded.task.definition).isEqualTo(definition)
        assertThat(succeeded.task.version).isEqualTo(task.version)
        assertThat(revisedTask.version).isGreaterThan(succeeded.task.version)
    }

    @Test
    fun `실패는 사유와 완료 시각을 남긴다`() {
        val running = pending().start(scheduledAt)
        val finishedAt = scheduledAt.plusSeconds(2)

        val failed = running.fail(finishedAt, "source unavailable")

        assertThat(failed.status).isEqualTo(CollectionRunStatus.FAILED)
        assertThat(failed.failureReason).isEqualTo("source unavailable")
        assertThat(failed.finishedAt).isEqualTo(finishedAt)
        assertThat(running.status).isEqualTo(CollectionRunStatus.RUNNING)
        assertThat(running.failureReason).isNull()
    }

    @Test
    fun `시작하지 않은 실행은 완료할 수 없고 실행 중에는 다시 시작할 수 없다`() {
        val pending = pending()
        val running = pending.start(scheduledAt)

        assertThatIllegalStateException().isThrownBy { pending.succeed(scheduledAt) }
        assertThatIllegalStateException().isThrownBy { pending.fail(scheduledAt, "failed") }
        assertThatIllegalStateException().isThrownBy { running.start(scheduledAt) }
    }

    @ParameterizedTest
    @EnumSource(value = CollectionRunStatus::class, names = ["SUCCEEDED", "FAILED"])
    fun `최종 상태에서는 어떤 상태 전이도 허용하지 않는다`(status: CollectionRunStatus) {
        val running = pending().start(scheduledAt)
        val terminal = if (status == CollectionRunStatus.SUCCEEDED) running.succeed(scheduledAt) else running.fail(scheduledAt, "failed")

        assertThatIllegalStateException().isThrownBy { terminal.start(scheduledAt) }
        assertThatIllegalStateException().isThrownBy { terminal.succeed(scheduledAt) }
        assertThatIllegalStateException().isThrownBy { terminal.fail(scheduledAt, "again") }
    }

    @Test
    fun `예정 시각 이전에 시작하거나 시작 시각 이전에 완료할 수 없다`() {
        val pending = pending()
        val running = pending.start(scheduledAt.plusSeconds(1))

        assertThatThrownBy { pending.start(scheduledAt.minusNanos(1)) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { running.succeed(scheduledAt) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { running.fail(scheduledAt, "failed") }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `예정 시작 완료 시각이 모두 같아도 허용한다`() {
        val running = pending().start(scheduledAt)

        assertThat(running.succeed(scheduledAt).finishedAt).isEqualTo(scheduledAt)
        assertThat(running.fail(scheduledAt, "failed").finishedAt).isEqualTo(scheduledAt)
    }

    @ParameterizedTest
    @ValueSource(strings = ["1969-12-31T23:59:59.999999999Z", "+10000-01-01T00:00:00Z"])
    fun `저장 가능한 연도 범위를 벗어난 예정 시각은 거부한다`(value: String) {
        assertThatThrownBy {
            CollectionRun.pending(
                UUID.randomUUID(),
                TaskSnapshot(task.id, task.version, task.definition, SourceSnapshot(source.id, source.definition, source.createdAt)),
                Instant.parse(value),
            )
        }.isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @ParameterizedTest
    @EnumSource(CollectionRunStatus::class)
    fun `상태에 맞는 시각과 사유로 복원한 실행은 같은 값을 돌려준다`(status: CollectionRunStatus) {
        val startedAt = if (status == CollectionRunStatus.PENDING) null else scheduledAt.plusSeconds(1)
        val finishedAt =
            if (status == CollectionRunStatus.SUCCEEDED ||
                status == CollectionRunStatus.FAILED
            ) {
                scheduledAt.plusSeconds(2)
            } else {
                null
            }
        val reason = if (status == CollectionRunStatus.FAILED) "source unavailable" else null

        val restored =
            CollectionRun.restore(
                UUID.randomUUID(),
                snapshot(),
                RunTimeline(scheduledAt, startedAt, finishedAt),
                RunState(status, reason),
            )

        assertThat(restored.status).isEqualTo(status)
        assertThat(restored.scheduledFor).isEqualTo(scheduledAt)
        assertThat(restored.startedAt).isEqualTo(startedAt)
        assertThat(restored.finishedAt).isEqualTo(finishedAt)
        assertThat(restored.failureReason).isEqualTo(reason)
    }

    @Test
    fun `복원한 실행도 정상 전이와 같은 방식으로 이어서 전이한다`() {
        val restored = CollectionRun.restore(UUID.randomUUID(), snapshot(), RunTimeline(scheduledAt), RunState(CollectionRunStatus.PENDING))

        assertThat(restored.start(scheduledAt).status).isEqualTo(CollectionRunStatus.RUNNING)
    }

    @Test
    fun `상태와 맞지 않는 시각이나 사유로는 복원할 수 없다`() {
        val started = scheduledAt.plusSeconds(1)
        val finished = scheduledAt.plusSeconds(2)
        val invalid =
            listOf(
                restoreCase(CollectionRunStatus.PENDING, started = started),
                restoreCase(CollectionRunStatus.PENDING, finished = finished),
                restoreCase(CollectionRunStatus.RUNNING),
                restoreCase(CollectionRunStatus.RUNNING, started = started, finished = finished),
                restoreCase(CollectionRunStatus.SUCCEEDED, started = started),
                restoreCase(CollectionRunStatus.SUCCEEDED, started = started, finished = finished, reason = "unexpected"),
                restoreCase(CollectionRunStatus.FAILED, started = started, finished = finished),
                restoreCase(CollectionRunStatus.FAILED, started = started, finished = finished, reason = " "),
                restoreCase(CollectionRunStatus.FAILED, started = started, finished = finished, reason = "x".repeat(1001)),
                restoreCase(CollectionRunStatus.RUNNING, started = started, reason = "unexpected"),
            )

        invalid.forEach { restore ->
            assertThatThrownBy(restore).isInstanceOf(InvalidCollectionConfigurationException::class.java)
        }
    }

    @Test
    fun `시각 순서가 어긋난 실행은 복원할 수 없다`() {
        val beforeSchedule = restoreCase(CollectionRunStatus.RUNNING, started = scheduledAt.minusNanos(1))
        val finishBeforeStart =
            restoreCase(CollectionRunStatus.SUCCEEDED, started = scheduledAt.plusSeconds(2), finished = scheduledAt.plusSeconds(1))

        assertThatThrownBy(beforeSchedule).isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy(finishBeforeStart).isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    private fun restoreCase(
        status: CollectionRunStatus,
        started: Instant? = null,
        finished: Instant? = null,
        reason: String? = null,
    ): () -> Unit =
        {
            CollectionRun.restore(UUID.randomUUID(), snapshot(), RunTimeline(scheduledAt, started, finished), RunState(status, reason))
        }

    private fun snapshot() =
        TaskSnapshot(task.id, task.version, task.definition, SourceSnapshot(source.id, source.definition, source.createdAt))

    private fun pending(): CollectionRun =
        CollectionRun.pending(
            UUID.randomUUID(),
            TaskSnapshot(task.id, task.version, task.definition, SourceSnapshot(source.id, source.definition, source.createdAt)),
            scheduledAt,
        )
}
