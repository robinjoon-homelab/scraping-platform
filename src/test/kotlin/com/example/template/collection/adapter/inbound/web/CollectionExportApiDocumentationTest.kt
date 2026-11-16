package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.model.ExportItem
import com.example.template.collection.application.model.ExportPage
import com.example.template.collection.application.model.ExportStatus
import com.example.template.collection.application.model.StoredCollectedRecord
import com.example.template.collection.application.port.input.CollectionExportUseCase
import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.run.CollectedRecord
import com.example.template.collection.domain.run.RecordIdentity
import com.example.template.collection.domain.run.RecordProvenance
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import com.example.template.support.web.RestDocsWebTest
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

class CollectionExportApiDocumentationTest : RestDocsWebTest() {
    private val id = UUID.randomUUID()
    private val exports = mockk<CollectionExportUseCase>()
    private val mvc = restDocsMockMvc(CollectionExportController(exports), ApiExceptionHandler(), operatorTokenFilter())

    @Test
    fun `configure persists pull scope without implying receiver processing`() {
        every { exports.configure(id, true) } returns ExportStatus(id, true, 0, 0)
        mvc
            .perform(
                put("/api/collection-tasks/{id}/export", id)
                    .authorized()
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"enabled":true}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andDo(
                documented(
                    "export-configure",
                    requestFields(fieldWithPath("enabled").description("인증된 pull 허용")),
                    responseFields(
                        fieldWithPath("taskId").description("계획 ID"),
                        fieldWithPath("enabled").description("Pull 허용 여부"),
                        fieldWithPath("publishedThrough").description("공개 순번, 소비자 완료 위치가 아님"),
                        fieldWithPath("publishedCount").description("공개된 보존 레코드 수"),
                    ),
                ),
            )
    }

    @Test
    fun `empty pull returns replayable continuation`() {
        every { exports.pull(id, 0, 20) } returns ExportPage(id, emptyList(), 0)
        mvc
            .perform(get("/api/collection-tasks/{id}/export/records", id).authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nextAfter").value(0))
            .andDo(
                documented(
                    "export-pull",
                    responseFields(
                        fieldWithPath("taskId").description("계획 범위"),
                        fieldWithPath("items").description("공개된 레코드 묶음"),
                        fieldWithPath("nextAfter").description("다음 after, 빈 페이지면 유지"),
                    ),
                ),
            )
    }

    @Test
    fun `export preserves content identity and immutable plan provenance`() {
        val now = Instant.parse("2026-11-16T00:00:00Z")
        val source = SourceSnapshot(UUID.randomUUID(), SourceDefinition("Public", SourceKind.HTTP_JSON, "https://example.com"), now)
        val task = TaskSnapshot(id, 2, TaskDefinition("Items", source.id, CollectionTarget("/items"), IntervalSchedule(60, now)), source)
        val record =
            CollectedRecord(
                RecordIdentity("/items", "a".repeat(64)),
                "http-json/1",
                "{}",
                RecordProvenance(UUID.randomUUID(), source.id, now),
            )
        val item = ExportItem(7, StoredCollectedRecord(UUID.randomUUID(), record), task)
        every { exports.pull(id, 0, 20) } returns ExportPage(id, listOf(item), 7)
        mvc
            .perform(get("/api/collection-tasks/{id}/export/records", id).authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].record.record.provenance.sourceId").value(source.id.toString()))
            .andExpect(jsonPath("$.items[0].task.version").value(2))
            .andDo(
                documented(
                    "export-record",
                    responseFields(
                        fieldWithPath("taskId").description("계획 범위"),
                        fieldWithPath("nextAfter").description("이어받기 공개 순번"),
                        fieldWithPath("items[].sequence").description("불변 공개 순번"),
                        fieldWithPath("items[].record.id").description("재전달에도 같은 레코드 ID"),
                        subsectionWithPath("items[].record.record.identity").description("원천 키 sourceRecordKey와 내용 해시 revisionHash"),
                        fieldWithPath("items[].record.record.schemaVersion").description("수집기 스키마"),
                        fieldWithPath("items[].record.record.payload").description("원본 JSON 응답 문자열"),
                        subsectionWithPath("items[].record.record.provenance").description("원본 출처 ID, 실행 ID, 수집·원천 수정 시각"),
                        subsectionWithPath("items[].task").description("실행 당시 계획 ID·버전·정의와 불변 출처 스냅샷"),
                    ),
                ),
            )
    }

    @Test
    fun `status is authenticated and missing plans and invalid positions are explicit`() {
        every { exports.status(id) } throws CollectionTaskNotFoundException(id)
        every { exports.pull(id, -1, 20) } throws InvalidCollectionConfigurationException("after must be nonnegative")
        mvc.perform(get("/api/collection-tasks/{id}/export", id)).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/collection-tasks/{id}/export", id).authorized()).andExpect(status().isNotFound)
        mvc
            .perform(get("/api/collection-tasks/{id}/export/records", id).param("after", "-1").authorized())
            .andExpect(status().isBadRequest)
    }
}
