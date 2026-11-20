package com.example.template.todo.adapter.inbound.web

import com.example.template.support.web.RestDocsWebTest
import com.example.template.support.web.documented
import com.example.template.support.web.problemFields
import com.example.template.todo.application.port.input.TodoNotFoundException
import com.example.template.todo.application.port.input.TodoUseCase
import com.example.template.todo.domain.todo.InvalidTodoTitleException
import com.example.template.todo.domain.todo.Todo
import com.example.template.todo.domain.todo.TodoId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

// The use case is a strict mock that answers only the inputs a test stubs. Title normalization and the domain limits
// are covered by TodoTest and TodoServiceTest; this test checks the HTTP responses the endpoint returns for requests,
// results, and failures.
class TodoApiDocumentationTest : RestDocsWebTest() {
    private val todoId: UUID = UUID.fromString("3f0c8c3e-5b1a-4f4e-9a52-6a1d2b7c9e10")
    private val secondTodoId: UUID = UUID.fromString("8a6e0f2d-1c3b-4d5e-8f70-9a1b2c3d4e5f")
    private val missingId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val createdAt: Instant = Instant.parse("2026-10-13T00:00:00Z")
    private val todoUseCase = mockk<TodoUseCase>()
    private val mockMvc = restDocsMockMvc(TodoController(todoUseCase), ApiExceptionHandler())

    @Test
    fun `create with an untrimmed title returns the created todo with its location`() {
        val todo = Todo.create(TodoId(todoId), "  Write the first feature  ", createdAt)
        every { todoUseCase.create("  Write the first feature  ") } returns todo

        create("""{"title":"  Write the first feature  "}""")
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/todos/$todoId"))
            .andExpect(content().json(todoJson(title = "Write the first feature", completed = false), JsonCompareMode.STRICT))
            .andDo(
                documented(
                    "todos-create",
                    requestFields(
                        fieldWithPath("title").description(
                            "1~200자 제목. 공백만 입력할 수 없으며 저장할 때 앞뒤 공백을 제거합니다.",
                        ),
                    ),
                    responseHeaders(headerWithName("Location").description("생성된 할 일의 조회 경로")),
                    responseFields(*todoFields()),
                ),
            )
    }

    @Test
    fun `get returns the todo of the path id`() {
        every { todoUseCase.get(TodoId(todoId)) } returns todo("Read the API documentation")

        mockMvc
            .perform(get("/api/todos/{id}", todoId))
            .andExpect(status().isOk)
            .andExpect(content().json(todoJson(title = "Read the API documentation", completed = false), JsonCompareMode.STRICT))
            .andDo(documented("todos-get", todoIdParameter(), responseFields(*todoFields())))
    }

    @Test
    fun `list returns the todos in the order the use case gives`() {
        val second = Todo.restore(TodoId(secondTodoId), "Write the first feature", completed = false, createdAt)
        every { todoUseCase.list() } returns listOf(todo("Read the API documentation"), second)

        mockMvc
            .perform(get("/api/todos"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(todoId.toString()))
            .andExpect(jsonPath("$[0].title").value("Read the API documentation"))
            .andExpect(jsonPath("$[1].id").value(secondTodoId.toString()))
            .andExpect(jsonPath("$[1].title").value("Write the first feature"))
            .andDo(documented("todos-list", responseFields(*todoFields("[]."))))
    }

    @Test
    fun `list returns an empty array when there are no todos`() {
        every { todoUseCase.list() } returns emptyList()

        mockMvc
            .perform(get("/api/todos"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]", JsonCompareMode.STRICT))
    }

    @Test
    fun `complete returns the completed todo`() {
        every { todoUseCase.complete(TodoId(todoId)) } returns todo("Write the first feature").complete()

        mockMvc
            .perform(patch("/api/todos/{id}/complete", todoId))
            .andExpect(status().isOk)
            .andExpect(content().json(todoJson(title = "Write the first feature", completed = true), JsonCompareMode.STRICT))
            .andDo(documented("todos-complete", todoIdParameter(), responseFields(*todoFields())))
    }

    @Test
    fun `blank title returns a documented bad request`() {
        create("""{"title":"   "}""")
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.instance").value("/api/todos"))
            .andDo(documented("todos-invalid-title", responseFields(*problemFields())))
    }

    @Test
    fun `empty oversized missing null and malformed titles are rejected`() {
        val invalidRequests = listOf("""{"title":""}""", """{"title":"${"x".repeat(201)}"}""", "{}", """{"title":null}""", "{")

        invalidRequests.forEach { request ->
            create(request)
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    @Test
    fun `title with exactly 200 characters is created`() {
        val title = "x".repeat(200)
        every { todoUseCase.create(title) } returns Todo.create(TodoId(todoId), title, createdAt)

        create("""{"title":"$title"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value(title))
    }

    // @NotBlank does not treat a non-breaking space as blank, so this title reaches the domain, which rejects it.
    @Test
    fun `title rejected by the domain returns a problem bad request`() {
        every { todoUseCase.create(" ") } throws InvalidTodoTitleException("title must not be blank")

        create("""{"title":" "}""")
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Invalid title"))
            .andExpect(jsonPath("$.detail").value("title must not be blank"))
    }

    @Test
    fun `get missing todo returns a documented not found`() {
        every { todoUseCase.get(TodoId(missingId)) } throws TodoNotFoundException(TodoId(missingId))

        mockMvc
            .perform(get("/api/todos/{id}", missingId))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Todo not found"))
            .andExpect(jsonPath("$.instance").value("/api/todos/$missingId"))
            .andDo(documented("todos-not-found", todoIdParameter(), responseFields(*problemFields())))
    }

    @Test
    fun `completion of a missing todo returns not found`() {
        every { todoUseCase.complete(TodoId(missingId)) } throws TodoNotFoundException(TodoId(missingId))

        mockMvc
            .perform(patch("/api/todos/{id}/complete", missingId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Todo not found"))
    }

    @Test
    fun `malformed id returns a problem bad request`() {
        listOf(get("/api/todos/{id}", "invalid-id"), patch("/api/todos/{id}/complete", "invalid-id")).forEach { request ->
            mockMvc
                .perform(request)
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    private fun create(body: String): ResultActions =
        mockMvc.perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content(body))

    private fun todo(title: String): Todo = Todo.restore(TodoId(todoId), title, completed = false, createdAt)

    private fun todoJson(
        title: String,
        completed: Boolean,
    ): String = """{"id":"$todoId","title":"$title","completed":$completed,"createdAt":"$createdAt"}"""

    private fun todoIdParameter() = pathParameters(parameterWithName("id").description("할 일의 UUID"))

    private fun todoFields(prefix: String = ""): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("${prefix}id").description("할 일의 UUID"),
            fieldWithPath("${prefix}title").description("할 일 제목"),
            fieldWithPath("${prefix}completed").description("완료 여부"),
            fieldWithPath("${prefix}createdAt").description("생성 시각, ISO 8601 UTC"),
        )
}
