package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.model.CollectionPreview
import com.example.template.collection.application.model.CollectionValidationResult
import com.example.template.collection.application.model.CollectorCapability
import com.example.template.collection.application.port.input.CollectionOperationsUseCase
import com.example.template.collection.domain.run.CollectionRun
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
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

class CollectionOperationsApiDocumentationTest : RestDocsWebTest() {
    private val operations = mockk<CollectionOperationsUseCase>()
    private val mvc = restDocsMockMvc(CollectionOperationsController(operations), ApiExceptionHandler(), operatorTokenFilter())
    private val id = UUID.randomUUID()

    @Test
    fun `capabilities distinguish executable collection from source registration`() {
        every { operations.capabilities() } returns listOf(CollectorCapability(SourceKind.HTTP_JSON, true, false, "whole JSON response"))
        mvc
            .perform(get("/api/collection/capabilities").authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].supported").value(true))
            .andDo(
                documented(
                    "collection-capabilities",
                    responseFields(
                        fieldWithPath("[].kind").description("출처 형식"),
                        fieldWithPath("[].supported").description("실행 구현 여부"),
                        fieldWithPath("[].credentialSupported").description("자격 증명 지원 여부"),
                        fieldWithPath("[].recordUnit").description("응답 전체가 하나의 레코드"),
                    ),
                ),
            )
    }

    @Test
    fun `manual execution accepts an idempotent versioned request and returns pending run`() {
        val now = Instant.parse("2026-11-16T00:00:00Z")
        val source = SourceSnapshot(UUID.randomUUID(), SourceDefinition("Public", SourceKind.HTTP_JSON, "https://example.com"), now)
        val definition = TaskDefinition("Items", source.id, CollectionTarget("/items"), IntervalSchedule(60, now))
        val run = CollectionRun.pending(UUID.randomUUID(), TaskSnapshot(id, 0, definition, source), now)
        every { operations.runNow(id, 0, "request-1") } returns run
        mvc
            .perform(
                post("/api/collection-tasks/{id}/runs", id)
                    .authorized()
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expectedVersion":0,"requestKey":"request-1"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andDo(
                documented(
                    "collection-run-now",
                    requestFields(
                        fieldWithPath("expectedVersion").description("예상 계획 버전"),
                        fieldWithPath("requestKey").description("계획별 1~120자 멱등 키, 같은 키·다른 버전은 409"),
                    ),
                    responseFields(*runFields()),
                ),
            )
    }

    @Test
    fun `validation exposes actionable issues and preview returns transient records`() {
        every { operations.validate(id) } returns CollectionValidationResult(false, listOf("source.kind: only HTTP_JSON is executable"))
        every { operations.preview(id) } returns CollectionPreview(emptyList(), "whole JSON response")
        mvc
            .perform(get("/api/collection-tasks/{id}/validation", id).authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executable").value(false))
            .andDo(
                documented(
                    "collection-validation",
                    responseFields(fieldWithPath("executable").description("실행 가능 여부"), fieldWithPath("issues").description("필드별 미지원 이유")),
                ),
            )
        mvc
            .perform(post("/api/collection-tasks/{id}/preview", id).authorized())
            .andExpect(status().isOk)
            .andDo(
                documented(
                    "collection-preview",
                    responseFields(
                        fieldWithPath("records").description("운영 저장 없이 얻은 샘플"),
                        fieldWithPath("recordUnit").description("레코드 단위"),
                    ),
                ),
            )
    }
}
