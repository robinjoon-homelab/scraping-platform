package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.port.input.CollectionExportUseCase
import com.example.template.collection.application.port.input.CollectionOperationsUseCase
import com.example.template.collection.application.port.input.CollectionPreviewFailedException
import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.application.port.input.CollectionTaskUseCase
import com.example.template.support.web.pageMockMvc
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class CollectionPageErrorControllerTest {
    private val exports = mockk<CollectionExportUseCase>()
    private val operations = mockk<CollectionOperationsUseCase>()
    private val tasks = mockk<CollectionTaskUseCase>()
    private val id = UUID.randomUUID()
    private val advice = CollectionPageErrorController()

    @Test
    fun `missing plan redirects with sanitized expected error`() {
        every { exports.status(id) } throws CollectionTaskNotFoundException(id)

        pageMockMvc(ExportPageController(exports), advice)
            .perform(get("/admin/plans/$id/export"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/admin/plans"))
            .andExpect(flash().attributeExists("noticeError"))
            .andExpect { result ->
                assertThat(result.flashMap["noticeError"].toString()).doesNotContain(id.toString(), "Exception")
            }
    }

    @Test
    fun `expected preview failure redirects without exposing connector details`() {
        every { operations.preview(id) } throws CollectionPreviewFailedException(IllegalStateException("private-connector-detail"))

        pageMockMvc(PlanOperationPageController(tasks, operations), advice)
            .perform(post("/admin/plans/$id/preview"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/admin/plans"))
            .andExpect(flash().attributeExists("noticeError"))
            .andExpect { result ->
                assertThat(result.flashMap["noticeError"].toString()).doesNotContain("private-connector-detail", "Exception")
            }
    }

    @ParameterizedTest
    @MethodSource("unexpectedFailures")
    fun `unexpected failures propagate to the container error dispatch with diagnostics`(failure: RuntimeException) {
        every { tasks.list(0, 20) } throws failure
        val mvc = pageMockMvc(PlanPageController(tasks, mockk(), operations), advice)

        // Standalone MockMvc has no servlet container error dispatch; the original cause must escape for Boot's 5xx handling.
        assertThatThrownBy { mvc.perform(get("/admin/plans")) }.hasRootCause(failure)
    }

    companion object {
        @JvmStatic
        fun unexpectedFailures(): List<RuntimeException> =
            listOf(
                DataAccessResourceFailureException("private-database-detail"),
                NullPointerException("private-programming-detail"),
            )
    }
}
