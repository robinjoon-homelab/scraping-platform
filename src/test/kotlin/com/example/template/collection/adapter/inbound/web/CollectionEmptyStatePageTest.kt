package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.port.input.CollectionOperationsUseCase
import com.example.template.collection.application.port.input.CollectionRunQueryUseCase
import com.example.template.collection.application.port.input.CollectionTaskUseCase
import com.example.template.collection.application.port.input.SourceUseCase
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import com.example.template.support.web.pageMockMvc
import com.example.template.support.web.renderedPage
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CollectionEmptyStatePageTest {
    private val sources = mockk<SourceUseCase>()
    private val tasks = mockk<CollectionTaskUseCase>()
    private val operations = mockk<CollectionOperationsUseCase>()
    private val runs = mockk<CollectionRunQueryUseCase>()
    private val id = UUID.randomUUID()
    private val now = Instant.parse("2026-11-16T00:00:00Z")
    private val source = Source.create(id, SourceDefinition("공개 원천", SourceKind.HTTP_JSON, "https://example.org"), now)
    private val task =
        CollectionTask.create(
            id,
            TaskDefinition("뉴스 정기 수집", id, CollectionTarget("/data"), IntervalSchedule(60, now)),
            now,
        )
    private val run =
        CollectionRun.pending(
            id,
            TaskSnapshot(id, 0, task.definition, SourceSnapshot(id, source.definition, now)),
            now,
        )

    @Test
    fun `sources list renders empty state only when empty`() {
        val mvc = pageMockMvc(SourcePageController(sources))
        every { sources.list(0, 20) } returns listOf(source)
        assertThat(mvc.renderedPage("/admin/sources", "empty-state-sources-populated"))
            .contains("공개 원천", "https://example.org")
            .doesNotContain("등록한 출처가 없습니다", "ui-empty")

        every { sources.list(0, 20) } returns emptyList()
        assertThat(mvc.renderedPage("/admin/sources", "empty-state-sources-empty"))
            .contains("등록한 출처가 없습니다", "ui-empty")
    }

    @Test
    fun `plans list renders empty state only when empty`() {
        val mvc = pageMockMvc(PlanPageController(tasks, sources, operations))
        every { tasks.list(0, 20) } returns listOf(task)
        assertThat(mvc.renderedPage("/admin/plans", "empty-state-plans-populated"))
            .contains("뉴스 정기 수집", "중지")
            .doesNotContain("수집 계획이 없습니다", "ui-empty")

        every { tasks.list(0, 20) } returns emptyList()
        assertThat(mvc.renderedPage("/admin/plans", "empty-state-plans-empty"))
            .contains("수집 계획이 없습니다", "ui-empty")
    }

    @Test
    fun `runs list renders empty state only when empty`() {
        val mvc = pageMockMvc(RunPageController(runs))
        every { runs.listRuns(id, 0, 20) } returns listOf(run)
        assertThat(mvc.renderedPage("/admin/plans/$id/runs", "empty-state-runs-populated"))
            .contains("대기", now.toString())
            .doesNotContain("실행 이력이 없습니다", "ui-empty")

        every { runs.listRuns(id, 0, 20) } returns emptyList()
        assertThat(mvc.renderedPage("/admin/plans/$id/runs", "empty-state-runs-empty"))
            .contains("실행 이력이 없습니다", "ui-empty")
    }
}
