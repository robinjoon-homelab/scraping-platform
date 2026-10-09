package com.example.template.adapter.inbound.web

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
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
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
class CollectionTaskApiDocumentationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dataSource: DataSource

    @BeforeEach
    fun clearDatabase() {
        dataSource.clearConfiguration()
    }

    @Test
    fun `create persists a disabled version zero task and its parameters`() {
        val sourceId = mockMvc.createSource()
        val result =
            mockMvc
                .perform(postTask(taskDefinition(sourceId, "  Market headlines  ")))
                .andExpect(status().isCreated)
                .andExpect(header().string("Location", startsWith("/api/collection-tasks/")))
                .andExpect(jsonPath("$.id").isNotEmpty)
                .andExpect(jsonPath("$.definition.name").value("Market headlines"))
                .andExpect(jsonPath("$.definition.target.parameters.topic").value("경제"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andDo(
                    documented(
                        "tasks-create",
                        requestFields(*taskDefinitionFields()),
                        responseHeaders(headerWithName("Location").description("생성된 수집 작업의 조회 경로")),
                        responseFields(*taskFields()),
                    ),
                ).andReturn()

        mockMvc
            .perform(get(checkNotNull(result.response.getHeader("Location"))).authorized())
            .andExpect(status().isOk)
            .andExpect(content().json(result.response.contentAsString))
        dataSource.assertConfigurationCount("collection_tasks", 1)
    }

    @Test
    fun `get returns the persisted task`() {
        val id = createTask(mockMvc.createSource())

        mockMvc
            .perform(get("/api/collection-tasks/{id}", id).authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.definition.name").value("Market headlines"))
            .andDo(documented("tasks-get", resourceIdParameter(), responseFields(*taskFields())))
    }

    @Test
    fun `list applies offset and limit to persisted tasks`() {
        val sourceId = mockMvc.createSource()
        createTask(sourceId, "First")
        val secondId = createTask(sourceId, "Second")
        createTask(sourceId, "Third")

        mockMvc
            .perform(get("/api/collection-tasks").authorized().queryParam("offset", "1").queryParam("limit", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(secondId.toString()))
            .andDo(documented("tasks-list", paginationParameters(), responseFields(*taskFields("[]."))))
    }

    @Test
    fun `empty list and invalid pagination are handled`() {
        mockMvc.perform(get("/api/collection-tasks").authorized()).andExpect(status().isOk).andExpect(content().json("[]"))
        listOf("offset=-1", "limit=0", "limit=101", "offset=invalid").forEach { query ->
            mockMvc.perform(get("/api/collection-tasks?$query").authorized()).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `replace changes the complete definition and increments version`() {
        val id = createTask(mockMvc.createSource())
        val replacementSourceId = mockMvc.createSource("Other source")
        val replacement = taskDefinition(replacementSourceId, "Technology headlines").replace("/articles", "/technology")
        val result =
            mockMvc
                .perform(
                    put("/api/collection-tasks/{id}", id)
                        .authorized()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"expectedVersion":0,"definition":$replacement}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.definition.name").value("Technology headlines"))
                .andExpect(jsonPath("$.definition.sourceId").value(replacementSourceId.toString()))
                .andExpect(jsonPath("$.definition.target.resource").value("/technology"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.version").value(1))
                .andDo(
                    documented(
                        "tasks-replace",
                        resourceIdParameter(),
                        requestFields(
                            fieldWithPath("expectedVersion").description("마지막 조회에서 받은 현재 버전"),
                            *taskDefinitionFields("definition."),
                        ),
                        responseFields(*taskFields()),
                    ),
                ).andReturn()

        mockMvc
            .perform(get("/api/collection-tasks/{id}", id).authorized())
            .andExpect(status().isOk)
            .andExpect(content().json(result.response.contentAsString))
        dataSource.assertConfigurationCount("collection_tasks", 1)
    }

    @Test
    fun `enabling persists state and stale writes return conflict`() {
        val id = createTask(mockMvc.createSource())
        mockMvc
            .perform(enableTask(id, 0))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.version").value(1))
            .andDo(
                documented(
                    "tasks-enabled",
                    resourceIdParameter(),
                    requestFields(
                        fieldWithPath("expectedVersion").description("마지막 조회에서 받은 현재 버전"),
                        fieldWithPath("enabled").description("수집 활성화 설정"),
                    ),
                    responseFields(*taskFields()),
                ),
            )
        mockMvc
            .perform(enableTask(id, 0))
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andDo(documented("configuration-conflict", resourceIdParameter(), responseFields(*problemFields())))
        mockMvc
            .perform(get("/api/collection-tasks/{id}", id).authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.version").value(1))
    }

    @Test
    fun `stale replacement does not change stored definition`() {
        val sourceId = mockMvc.createSource()
        val id = createTask(sourceId)
        mockMvc.perform(enableTask(id, 0)).andExpect(status().isOk)
        mockMvc
            .perform(
                put("/api/collection-tasks/{id}", id)
                    .authorized()
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expectedVersion":0,"definition":${taskDefinition(sourceId, "Stale name")}}"""),
            ).andExpect(status().isConflict)
        mockMvc
            .perform(get("/api/collection-tasks/{id}", id).authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.definition.name").value("Market headlines"))
            .andExpect(jsonPath("$.version").value(1))
    }

    @Test
    fun `missing task or source returns not found without inserting`() {
        val missingId = UUID.randomUUID()
        mockMvc.perform(get("/api/collection-tasks/{id}", missingId).authorized()).andExpect(status().isNotFound)
        mockMvc.perform(enableTask(missingId, 0)).andExpect(status().isNotFound)
        mockMvc.perform(postTask(taskDefinition(missingId))).andExpect(status().isNotFound)
        dataSource.assertConfigurationCount("collection_tasks", 0)
    }

    @Test
    fun `invalid nested values and malformed requests are rejected`() {
        val definition = taskDefinition(mockMvc.createSource())
        val invalidRequests =
            listOf(
                "{}",
                "{",
                definition.replace("Market headlines", " "),
                definition.replace("/articles", "//other.example/articles"),
                definition.replace("/articles", "/../private"),
                definition.replace("/articles", "/articles?secret=value"),
                definition.replace("3600", "59"),
                definition.replace("3600", "31536001"),
                definition.replace("\"topic\":\"경제\"", "\"topic\":null"),
                definition.replace("\"everySeconds\":3600,", ""),
                definition.replace("3600", "null"),
                definition.replace("2026-01-01T00:00:00Z", "invalid-date"),
            )
        invalidRequests.forEach { request ->
            mockMvc
                .perform(postTask(request))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.status").value(400))
        }
        mockMvc.perform(get("/api/collection-tasks/{id}", "invalid-id").authorized()).andExpect(status().isBadRequest)
        dataSource.assertConfigurationCount("collection_tasks", 0)
    }

    @Test
    fun `negative version and missing or null state fields are rejected`() {
        val sourceId = mockMvc.createSource()
        val id = createTask(sourceId)
        mockMvc.perform(enableTask(id, -1)).andExpect(status().isBadRequest)
        val invalidRequests =
            listOf(
                """{"expectedVersion":0}""",
                """{"enabled":true}""",
                """{"expectedVersion":null,"enabled":true}""",
                """{"expectedVersion":0,"enabled":null}""",
            )
        invalidRequests.forEach { request ->
            mockMvc
                .perform(
                    patch("/api/collection-tasks/{id}/enabled", id)
                        .authorized()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request),
                ).andExpect(status().isBadRequest)
        }
        mockMvc
            .perform(
                put("/api/collection-tasks/{id}", id)
                    .authorized()
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"definition":${taskDefinition(sourceId)}}"""),
            ).andExpect(status().isBadRequest)
        mockMvc
            .perform(get("/api/collection-tasks/{id}", id).authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.version").value(0))
    }

    private fun createTask(
        sourceId: UUID,
        name: String = "Market headlines",
    ): UUID {
        val location =
            mockMvc
                .perform(postTask(taskDefinition(sourceId, name)))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getHeader("Location")
        return UUID.fromString(checkNotNull(location).substringAfterLast('/'))
    }

    private fun taskDefinition(
        sourceId: UUID,
        name: String = "Market headlines",
    ) = """
        {"name":"$name","sourceId":"$sourceId",
         "target":{"resource":"/articles","parameters":{"topic":"경제","language":"ko"}},
         "schedule":{"everySeconds":3600,"anchorAt":"2026-01-01T00:00:00Z"}}
        """.trimIndent()

    private fun postTask(body: String) = post("/api/collection-tasks").authorized().contentType(MediaType.APPLICATION_JSON).content(body)

    private fun enableTask(
        id: UUID,
        expectedVersion: Long,
    ) = patch("/api/collection-tasks/{id}/enabled", id)
        .authorized()
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"expectedVersion":$expectedVersion,"enabled":true}""")
}
