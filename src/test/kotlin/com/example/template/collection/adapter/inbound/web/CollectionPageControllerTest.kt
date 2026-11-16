package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.model.CollectedRecordSummary
import com.example.template.collection.application.model.CollectionPreview
import com.example.template.collection.application.model.CollectionValidationResult
import com.example.template.collection.application.model.ExportItem
import com.example.template.collection.application.model.ExportPage
import com.example.template.collection.application.model.ExportStatus
import com.example.template.collection.application.model.StoredCollectedRecord
import com.example.template.collection.application.port.input.CollectionExportUseCase
import com.example.template.collection.application.port.input.CollectionOperationsUseCase
import com.example.template.collection.application.port.input.CollectionRunQueryUseCase
import com.example.template.collection.application.port.input.CollectionTaskUseCase
import com.example.template.collection.application.port.input.SourceUseCase
import com.example.template.collection.domain.run.CollectedRecord
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.RecordIdentity
import com.example.template.collection.domain.run.RecordProvenance
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

class CollectionPageControllerTest {
    private val sources = mockk<SourceUseCase>()
    private val tasks = mockk<CollectionTaskUseCase>()
    private val operations = mockk<CollectionOperationsUseCase>()
    private val runs = mockk<CollectionRunQueryUseCase>()
    private val exports = mockk<CollectionExportUseCase>()
    private val id = UUID.randomUUID()
    private val now = Instant.parse("2026-11-16T00:00:00Z")
    private val source = Source.create(id, SourceDefinition("공개 원천", SourceKind.HTTP_JSON, "https://example.org"), now)
    private val task =
        CollectionTask.create(
            id,
            TaskDefinition("<script>alert(1)</script>", id, CollectionTarget("/data"), IntervalSchedule(60, now)),
            now,
        )
    private val run = CollectionRun.pending(id, TaskSnapshot(id, 0, task.definition, SourceSnapshot(id, source.definition, now)), now)
    private val record =
        StoredCollectedRecord(
            id,
            CollectedRecord(
                RecordIdentity("source-key", "a".repeat(64)),
                "json-v1",
                "<script>payload</script>",
                RecordProvenance(id, id, now),
            ),
        )

    @Test
    fun `sources and empty list render`() {
        every { sources.list(0, 20) } returns listOf(source)
        val mvc = pageMockMvc(SourcePageController(sources))
        assertThat(mvc.renderedPage("/admin/sources", "sources")).contains("공개 원천", "https://example.org")
        every { sources.list(0, 20) } returns emptyList()
        assertThat(mvc.renderedPage("/admin/sources", "sources-empty")).contains("등록한 출처가 없습니다")
    }

    @Test
    fun `plan list and create form render escaped names`() {
        every { tasks.list(0, 20) } returns listOf(task)
        every { sources.list(0, 100) } returns listOf(source)
        val mvc = pageMockMvc(PlanPageController(tasks, sources, operations))
        assertThat(mvc.renderedPage("/admin/plans", "plans")).contains("&lt;script&gt;").doesNotContain("<script>alert")
        assertThat(mvc.renderedPage("/admin/plans/new", "plan-new")).contains("name=\"everySeconds\"", "name=\"parameters\"")
    }

    @Test
    fun `plan detail renders validation and real actions`() {
        every { tasks.get(id) } returns task
        every { sources.get(id) } returns source
        every { operations.validate(id) } returns CollectionValidationResult(true, emptyList())
        val html = pageMockMvc(PlanPageController(tasks, sources, operations)).renderedPage("/admin/plans/$id", "plan-detail")
        assertThat(html).contains("/preview", "/run", "/enabled", "requestKey", "실행 가능한 설정")
    }

    @Test
    fun `run list detail and provenance render`() {
        every { runs.listRuns(id, 0, 20) } returns listOf(run)
        every { runs.getRun(id) } returns run
        every { runs.listRecords(id, 0, 20) } returns listOf(CollectedRecordSummary(id, record.record.identity, "json-v1", now, null, 25))
        every { runs.getRecord(id) } returns record
        val mvc = pageMockMvc(RunPageController(runs))
        assertThat(mvc.renderedPage("/admin/plans/$id/runs", "runs")).contains("대기")
        assertThat(mvc.renderedPage("/admin/runs/$id", "run")).contains("source-key", "미시작")
        assertThat(
            mvc.renderedPage("/admin/records/$id", "record"),
        ).contains("&lt;script&gt;payload", "a".repeat(64)).doesNotContain("<script>payload")
    }

    @Test
    fun `export renders scoped pull contract and positions`() {
        every { exports.status(id) } returns ExportStatus(id, true, 1, 1)
        every { exports.pull(id, 0, 20) } returns ExportPage(id, listOf(ExportItem(1, record, run.task)), 1)
        val mvc = pageMockMvc(ExportPageController(exports))
        assertThat(
            mvc.renderedPage("/admin/plans/$id/export", "export"),
        ).contains("/api/collection-tasks/$id/export/records", "Authorization: Bearer", "소비자가 받았거나 처리했다는 뜻이 아닙니다")
        assertThat(mvc.renderedPage("/admin/plans/$id/export/records", "export-records")).contains("after=1", "source-key")
    }

    @Test
    fun `manual run redirects to idempotent queued result`() {
        every { operations.runNow(id, 0, "same-key") } returns run
        val mvc = pageMockMvc(PlanOperationPageController(tasks, operations))
        repeat(2) {
            mvc
                .perform(post("/admin/plans/$id/run").param("version", "0").param("requestKey", "same-key"))
                .andExpect(status().isFound)
                .andExpect(redirectedUrl("/admin/runs/$id"))
        }
    }

    @Test
    fun `invalid form redirects with useful error`() {
        pageMockMvc(SourcePageController(sources))
            .perform(post("/admin/sources").param("name", "").param("baseUrl", ""))
            .andExpect(status().isFound)
            .andExpect(flash().attributeExists("noticeError"))
    }

    @Test
    fun `source and plan forms bind and redirect after save`() {
        every { sources.create(source.definition) } returns source
        pageMockMvc(
            SourcePageController(sources),
        ).perform(post("/admin/sources").param("name", "공개 원천").param("baseUrl", "https://example.org"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/admin/sources"))
        every { tasks.create(task.definition) } returns task
        pageMockMvc(PlanPageController(tasks, sources, operations))
            .perform(
                post("/admin/plans")
                    .param("name", task.definition.name)
                    .param("sourceId", id.toString())
                    .param("resource", "/data")
                    .param("everySeconds", "60")
                    .param("anchorAt", now.toString()),
            ).andExpect(status().isFound)
            .andExpect(redirectedUrl("/admin/plans/$id"))
    }

    @Test
    fun `preview and activation use redirects with operation outcomes`() {
        every { operations.preview(id) } returns CollectionPreview(listOf(record.record), "응답 묶음")
        every { tasks.setEnabled(id, 0, true) } returns task
        val mvc = pageMockMvc(PlanOperationPageController(tasks, operations))
        mvc
            .perform(post("/admin/plans/$id/preview"))
            .andExpect(status().isFound)
            .andExpect(flash().attributeExists("preview", "notice"))
        mvc
            .perform(post("/admin/plans/$id/enabled").param("version", "0").param("enabled", "true"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/admin/plans/$id"))
    }

    @Test
    fun `admin session cannot replace bearer API token`() {
        val mvc =
            org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(CollectionTaskController(tasks))
                .addFilters<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(OperatorTokenFilter("test-api-token"))
                .build()
        mvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/api/collection-tasks")
                    .session(
                        org.springframework.mock.web
                            .MockHttpSession(),
                    ),
            ).andExpect(status().isUnauthorized)
    }
}
