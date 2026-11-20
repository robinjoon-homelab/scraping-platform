package com.example.template.todo.adapter.inbound.web

import com.example.template.support.web.pageMockMvc
import com.example.template.support.web.renderedPage
import com.example.template.support.web.saveRenderedPage
import com.example.template.todo.application.port.input.TodoNotFoundException
import com.example.template.todo.application.port.input.TodoUseCase
import com.example.template.todo.domain.todo.InvalidTodoTitleException
import com.example.template.todo.domain.todo.Todo
import com.example.template.todo.domain.todo.TodoId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

// The use case is a strict mock that answers only what a test stubs; the tests check the rendered page and the redirects.
class TodoPageControllerTest {
    private val openId: UUID = UUID.fromString("3f0c8c3e-5b1a-4f4e-9a52-6a1d2b7c9e10")
    private val doneId: UUID = UUID.fromString("8a6e0f2d-1c3b-4d5e-8f70-9a1b2c3d4e5f")
    private val createdAt: Instant = Instant.parse("2026-10-13T00:00:00Z")
    private val todoUseCase = mockk<TodoUseCase>()
    private val mockMvc = pageMockMvc(TodoPageController(todoUseCase))

    @Test
    fun `empty list explains what to do`() {
        every { todoUseCase.list() } returns emptyList()

        val html = mockMvc.renderedPage("/admin/todos", "todos-empty")

        assertThat(html).contains("아직 할 일이 없습니다").doesNotContain("<table").contains("aria-current=\"page\"")
    }

    @Test
    fun `rows show state and the complete action`() {
        every { todoUseCase.list() } returns
            listOf(
                Todo.create(TodoId(openId), "첫 일", createdAt),
                Todo.restore(TodoId(doneId), "끝난 일", true, createdAt),
            )

        val html = mockMvc.renderedPage("/admin/todos", "todos-rows")

        assertThat(html)
            .contains("첫 일", "끝난 일", "남은 할 일 1개", "ui-badge-success")
            .contains("action=\"/admin/todos/$openId/complete\"")
            .doesNotContain("action=\"/admin/todos/$doneId/complete\"")
    }

    @Test
    fun `title is escaped`() {
        every { todoUseCase.list() } returns listOf(Todo.restore(TodoId(openId), "<script>x</script>", false, createdAt))

        val html = mockMvc.renderedPage("/admin/todos", "todos-escaped")

        assertThat(html).contains("&lt;script&gt;x&lt;/script&gt;").doesNotContain("<script>x")
    }

    @Test
    fun `created todo redirects with a notice`() {
        every { todoUseCase.create("새 일") } returns Todo.create(TodoId(openId), "새 일", createdAt)

        mockMvc
            .perform(post("/admin/todos").param("title", "새 일"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/admin/todos"))
            .andExpect(flash().attribute("notice", "추가했습니다."))
            .andExpect(flash().attribute("noticeError", false))
    }

    @Test
    fun `blank title is rejected on the page`() {
        mockMvc
            .perform(post("/admin/todos").param("title", " "))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/admin/todos"))
            .andExpect(flash().attribute("notice", "제목을 입력하세요."))
            .andExpect(flash().attribute("noticeError", true))
    }

    @Test
    fun `title length boundary`() {
        val longest = "a".repeat(200)
        every { todoUseCase.create(longest) } returns Todo.create(TodoId(openId), longest, createdAt)

        mockMvc
            .perform(post("/admin/todos").param("title", longest))
            .andExpect(flash().attribute("notice", "추가했습니다."))
        mockMvc
            .perform(post("/admin/todos").param("title", longest + "a"))
            .andExpect(flash().attribute("notice", "제목은 200자 이하로 입력하세요."))
            .andExpect(flash().attribute("noticeError", true))
    }

    @Test
    fun `domain rejection becomes a notice`() {
        every { todoUseCase.create("거부될 일") } throws InvalidTodoTitleException("title must not be blank")

        mockMvc
            .perform(post("/admin/todos").param("title", "거부될 일"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/admin/todos"))
            .andExpect(flash().attribute("noticeError", true))
            .andExpect(header().doesNotExist("Content-Type"))
    }

    @Test
    fun `complete redirects with a notice`() {
        every { todoUseCase.complete(TodoId(openId)) } returns Todo.restore(TodoId(openId), "첫 일", true, createdAt)

        mockMvc
            .perform(post("/admin/todos/$openId/complete"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/admin/todos"))
            .andExpect(flash().attribute("notice", "완료했습니다."))
            .andExpect(flash().attribute("noticeError", false))
    }

    @Test
    fun `missing todo becomes a notice`() {
        every { todoUseCase.complete(TodoId(openId)) } throws TodoNotFoundException(TodoId(openId))

        mockMvc
            .perform(post("/admin/todos/$openId/complete"))
            .andExpect(status().isFound)
            .andExpect(flash().attribute("notice", "이미 삭제되었거나 없는 항목입니다."))
            .andExpect(flash().attribute("noticeError", true))
    }

    @Test
    fun `flash notice is shown`() {
        every { todoUseCase.list() } returns emptyList()

        val html =
            mockMvc
                .perform(get("/admin/todos").flashAttr("notice", "저장하지 못했습니다.").flashAttr("noticeError", true))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(StandardCharsets.UTF_8)
        saveRenderedPage("todos-notice-error", html)

        assertThat(html).contains("role=\"status\"", "ui-notice-error", "저장하지 못했습니다.")
    }
}
