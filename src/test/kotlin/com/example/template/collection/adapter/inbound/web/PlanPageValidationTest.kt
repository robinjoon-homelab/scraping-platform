package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.port.input.CollectionOperationsUseCase
import com.example.template.collection.application.port.input.CollectionTaskUseCase
import com.example.template.collection.application.port.input.SourceUseCase
import com.example.template.support.web.pageMockMvc
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class PlanPageValidationTest {
    private val tasks = mockk<CollectionTaskUseCase>()
    private val sources = mockk<SourceUseCase>()
    private val operations = mockk<CollectionOperationsUseCase>()
    private val mvc = pageMockMvc(PlanPageController(tasks, sources, operations), CollectionPageErrorController())
    private val sourceId = UUID.randomUUID()
    private val planId = UUID.randomUUID()

    @Test
    fun `create plan with malformed anchorAt redirects with Korean error flash`() {
        assertMalformedAnchorRedirects(post("/admin/plans"))
    }

    @Test
    fun `update plan with malformed anchorAt redirects with Korean error flash`() {
        assertMalformedAnchorRedirects(post("/admin/plans/$planId").param("version", "0"))
    }

    private fun assertMalformedAnchorRedirects(request: MockHttpServletRequestBuilder) {
        mvc
            .perform(
                request
                    .param("name", "정기 수집 계획")
                    .param("sourceId", sourceId.toString())
                    .param("resource", "/data")
                    .param("everySeconds", "60")
                    .param("anchorAt", "2026-11-16"),
            ).andExpect(status().isFound)
            .andExpect(redirectedUrl("/admin/plans"))
            .andExpect(flash().attributeExists("noticeError"))
            .andExpect { result ->
                assertThat(result.flashMap["noticeError"].toString())
                    .contains("저장하지 못했습니다", "입력 형식")
                    .doesNotContain("DateTimeParseException", "2026-11-16")
            }
    }
}
