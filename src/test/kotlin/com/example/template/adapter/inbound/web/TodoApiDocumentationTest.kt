package com.example.template.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.snippet.Snippet
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(uriScheme = "https", uriHost = "api.example.com", uriPort = 443)
@ActiveProfiles("test")
class TodoApiDocumentationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dataSource: DataSource

    @BeforeEach
    fun clearDatabase() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate("DELETE FROM todos") }
        }
    }

    @Test
    fun `create returns a location and persists the normalized title`() {
        val result =
            mockMvc
                .perform(
                    post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"  Write the first feature  "}"""),
                ).andExpect(status().isCreated)
                .andExpect(header().string("Location", startsWith("/api/todos/")))
                .andExpect(jsonPath("$.id").isNotEmpty)
                .andExpect(jsonPath("$.title").value("Write the first feature"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.createdAt").isNotEmpty)
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
                ).andReturn()

        val location = checkNotNull(result.response.getHeader("Location"))
        val id = UUID.fromString(location.substringAfterLast('/'))
        assertStoredTodo(id, "Write the first feature", completed = false)
        mockMvc
            .perform(get("/api/todos/{id}", id))
            .andExpect(status().isOk)
            .andExpect(content().json(result.response.contentAsString))
    }

    @Test
    fun `get returns the persisted todo`() {
        val id = createTodo("Read the API documentation")

        mockMvc
            .perform(get("/api/todos/{id}", id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.title").value("Read the API documentation"))
            .andExpect(jsonPath("$.completed").value(false))
            .andDo(documented("todos-get", todoIdParameter(), responseFields(*todoFields())))
    }

    @Test
    fun `list returns persisted todos`() {
        createTodo("Read the API documentation")
        createTodo("Write the first feature")

        mockMvc
            .perform(get("/api/todos"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].title").value("Read the API documentation"))
            .andExpect(jsonPath("$[1].title").value("Write the first feature"))
            .andDo(documented("todos-list", responseFields(*todoFields("[]."))))
    }

    @Test
    fun `list returns an empty array when there are no todos`() {
        mockMvc
            .perform(get("/api/todos"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `complete persists the state and is idempotent`() {
        val id = createTodo("Write the first feature")

        val firstResponse =
            mockMvc
                .perform(patch("/api/todos/{id}/complete", id))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.completed").value(true))
                .andDo(documented("todos-complete", todoIdParameter(), responseFields(*todoFields())))
                .andReturn()
                .response.contentAsString

        mockMvc
            .perform(patch("/api/todos/{id}/complete", id))
            .andExpect(status().isOk)
            .andExpect(content().json(firstResponse))
        assertStoredTodo(id, "Write the first feature", completed = true)
    }

    @Test
    fun `blank title returns a documented bad request`() {
        mockMvc
            .perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content("""{"title":"   "}"""))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andDo(documented("todos-invalid-title", responseFields(*problemFields())))
        assertTodoCount(0)
    }

    @Test
    fun `empty oversized missing and null titles are rejected without persisting`() {
        val invalidRequests = listOf("""{"title":""}""", """{"title":"${"x".repeat(201)}"}""", "{}", """{"title":null}""")

        invalidRequests.forEach { request ->
            mockMvc
                .perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest)
        }
        assertTodoCount(0)
    }

    @Test
    fun `nonbreaking space title returns bad request after normalization`() {
        mockMvc
            .perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content("""{"title":"\u00a0"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
        assertTodoCount(0)
    }

    @Test
    fun `title with exactly 200 characters is accepted`() {
        val title = "x".repeat(200)

        mockMvc
            .perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content("""{"title":"$title"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value(title))
        assertTodoCount(1)
    }

    @Test
    fun `get missing todo returns a documented not found`() {
        mockMvc
            .perform(get("/api/todos/{id}", UUID.fromString("00000000-0000-0000-0000-000000000000")))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andDo(documented("todos-not-found", todoIdParameter(), responseFields(*problemFields())))
    }

    @Test
    fun `completion of missing todo does not create it`() {
        mockMvc
            .perform(patch("/api/todos/{id}/complete", UUID.randomUUID()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
        assertTodoCount(0)
    }

    @Test
    fun `malformed UUID and malformed JSON return bad request`() {
        mockMvc.perform(get("/api/todos/{id}", "invalid-id")).andExpect(status().isBadRequest)
        mockMvc
            .perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest)
        assertTodoCount(0)
    }

    private fun createTodo(title: String): UUID {
        val location =
            mockMvc
                .perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content("""{"title":"$title"}"""))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getHeader("Location")
        return UUID.fromString(checkNotNull(location).substringAfterLast('/'))
    }

    private fun assertStoredTodo(
        id: UUID,
        title: String,
        completed: Boolean,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT title, completed FROM todos WHERE id = ?").use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { result ->
                    assertThat(result.next()).isTrue()
                    assertThat(result.getString("title")).isEqualTo(title)
                    assertThat(result.getBoolean("completed")).isEqualTo(completed)
                    assertThat(result.next()).isFalse()
                }
            }
        }
    }

    private fun assertTodoCount(expected: Int) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM todos").use { result ->
                    assertThat(result.next()).isTrue()
                    assertThat(result.getInt(1)).isEqualTo(expected)
                }
            }
        }
    }

    private fun documented(
        identifier: String,
        vararg snippets: Snippet,
    ) = document(identifier, preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()), *snippets)

    private fun todoIdParameter() = pathParameters(parameterWithName("id").description("할 일의 UUID"))

    private fun todoFields(prefix: String = ""): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("${prefix}id").description("할 일의 UUID"),
            fieldWithPath("${prefix}title").description("할 일 제목"),
            fieldWithPath("${prefix}completed").description("완료 여부"),
            fieldWithPath("${prefix}createdAt").description("생성 시각, ISO 8601 UTC"),
        )

    private fun problemFields(): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("type")
                .type(JsonFieldType.STRING)
                .optional()
                .description("오류 유형 URI. 생략되면 RFC 9457 기본값 about:blank입니다."),
            fieldWithPath("title").description("오류 요약"),
            fieldWithPath("status").description("HTTP 상태 코드"),
            fieldWithPath("detail").description("오류의 구체적인 원인"),
            fieldWithPath("instance").description("오류가 발생한 요청 경로"),
        )
}
