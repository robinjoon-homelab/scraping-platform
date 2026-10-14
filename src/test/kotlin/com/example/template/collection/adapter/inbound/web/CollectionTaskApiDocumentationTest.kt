package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.application.port.input.CollectionTaskUseCase
import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.input.StaleTaskVersionException
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import com.example.template.support.web.RestDocsWebTest
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
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
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

// Versioning, name trimming and source checks are covered by the domain and CollectionTaskServiceTest; this test
// checks how the endpoint turns requests, use case results and failures into HTTP responses.
class CollectionTaskApiDocumentationTest : RestDocsWebTest() {
    private val taskId: UUID = UUID.fromString("4e67ebda-dfbd-47d5-9373-ab9a97e7675a")
    private val secondTaskId: UUID = UUID.fromString("8a6e0f2d-1c3b-4d5e-8f70-9a1b2c3d4e5f")
    private val sourceId: UUID = UUID.fromString("b1e38ed1-1d8a-4078-9322-a17fa7e30ec8")
    private val otherSourceId: UUID = UUID.fromString("3f0c8c3e-5b1a-4f4e-9a52-6a1d2b7c9e10")
    private val missingId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val createdAt: Instant = Instant.parse("2026-10-05T00:00:00.123456Z")
    private val taskUseCase = mockk<CollectionTaskUseCase>()
    private val mockMvc = restDocsMockMvc(CollectionTaskController(taskUseCase), ApiExceptionHandler(), operatorTokenFilter())

    @Test
    fun `create returns the created task with its location`() {
        val definition = definition(sourceId, "  Market headlines  ")
        every { taskUseCase.create(definition) } returns CollectionTask.create(taskId, definition, createdAt)

        mockMvc
            .perform(postTask(taskDefinitionJson(sourceId, "  Market headlines  ")))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/collection-tasks/$taskId"))
            .andExpect(
                content().json(
                    taskJson(taskId, enabled = false, version = 0, nextRunAt = null),
                    JsonCompareMode.STRICT,
                ),
            ).andDo(
                documented(
                    "tasks-create",
                    requestFields(*taskDefinitionFields()),
                    responseHeaders(headerWithName("Location").description("생성된 수집 작업의 조회 경로")),
                    responseFields(*taskFields()),
                ),
            )
    }

    @Test
    fun `get returns the task of the path id`() {
        every { taskUseCase.get(taskId) } returns task(taskId)

        mockMvc
            .perform(get("/api/collection-tasks/{id}", taskId).authorized())
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    taskJson(taskId, enabled = false, version = 0, nextRunAt = null),
                    JsonCompareMode.STRICT,
                ),
            ).andDo(documented("tasks-get", resourceIdParameter(), responseFields(*taskFields())))
    }

    @Test
    fun `list returns the requested page`() {
        every { taskUseCase.list(1, 1) } returns listOf(task(secondTaskId, "Second"))

        mockMvc
            .perform(get("/api/collection-tasks").authorized().queryParam("offset", "1").queryParam("limit", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(secondTaskId.toString()))
            .andExpect(jsonPath("$[0].definition.name").value("Second"))
            .andDo(documented("tasks-list", paginationParameters(), responseFields(*taskFields("[]."))))
    }

    @Test
    fun `default empty list and rejected or malformed pagination are handled`() {
        every { taskUseCase.list(0, 50) } returns emptyList()
        every { taskUseCase.list(0, 101) } throws InvalidCollectionConfigurationException("Limit must be between 1 and 100")

        mockMvc
            .perform(get("/api/collection-tasks").authorized())
            .andExpect(status().isOk)
            .andExpect(content().json("[]", JsonCompareMode.STRICT))
        listOf("limit=101", "offset=invalid").forEach { query ->
            mockMvc
                .perform(get("/api/collection-tasks?$query").authorized())
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    @Test
    fun `replace returns the revised task`() {
        val replacement = definition(otherSourceId, "Technology headlines", "/technology")
        every { taskUseCase.replace(taskId, 0, replacement) } returns task(taskId).revise(replacement, createdAt)
        val body = """{"expectedVersion":0,"definition":${taskDefinitionJson(otherSourceId, "Technology headlines", "/technology")}}"""

        mockMvc
            .perform(replaceTask(taskId, body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.definition.name").value("Technology headlines"))
            .andExpect(jsonPath("$.definition.sourceId").value(otherSourceId.toString()))
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
            )
    }

    @Test
    fun `enabling returns the enabled task`() {
        every { taskUseCase.setEnabled(taskId, 0, true) } returns task(taskId).changeEnabled(true, createdAt)

        mockMvc
            .perform(enableTask(taskId, 0))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    taskJson(taskId, enabled = true, version = 1, nextRunAt = "2026-10-05T01:00:00Z"),
                    JsonCompareMode.STRICT,
                ),
            ).andDo(
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
    }

    @Test
    fun `stale versions return a documented conflict`() {
        every { taskUseCase.setEnabled(taskId, 0, true) } throws StaleTaskVersionException(taskId)
        every { taskUseCase.replace(taskId, 0, definition(sourceId, "Stale name")) } throws StaleTaskVersionException(taskId)

        mockMvc
            .perform(enableTask(taskId, 0))
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.title").value("Collection task version conflict"))
            .andDo(documented("configuration-conflict", resourceIdParameter(), responseFields(*problemFields())))
        mockMvc
            .perform(replaceTask(taskId, """{"expectedVersion":0,"definition":${taskDefinitionJson(sourceId, "Stale name")}}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun `missing task or source returns not found`() {
        every { taskUseCase.get(missingId) } throws CollectionTaskNotFoundException(missingId)
        every { taskUseCase.setEnabled(missingId, 0, true) } throws CollectionTaskNotFoundException(missingId)
        every { taskUseCase.create(definition(missingId)) } throws SourceNotFoundException(missingId)

        listOf(get("/api/collection-tasks/{id}", missingId).authorized(), enableTask(missingId, 0)).forEach { request ->
            mockMvc
                .perform(request)
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.title").value("Collection task not found"))
        }
        mockMvc
            .perform(postTask(taskDefinitionJson(missingId)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.title").value("Source not found"))
            .andExpect(jsonPath("$.instance").value("/api/collection-tasks"))
    }

    @Test
    fun `invalid nested values and malformed requests are rejected`() {
        val definition = taskDefinitionJson(sourceId)
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
    }

    @Test
    fun `missing or null state fields and a rejected version are bad requests`() {
        every { taskUseCase.setEnabled(taskId, -1, true) } throws
            InvalidCollectionConfigurationException("Expected version must not be negative")
        val invalidRequests =
            listOf(
                """{"expectedVersion":0}""",
                """{"enabled":true}""",
                """{"expectedVersion":null,"enabled":true}""",
                """{"expectedVersion":0,"enabled":null}""",
            )

        mockMvc.perform(enableTask(taskId, -1)).andExpect(status().isBadRequest)
        invalidRequests.forEach { request ->
            mockMvc
                .perform(
                    patch("/api/collection-tasks/{id}/enabled", taskId)
                        .authorized()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request),
                ).andExpect(status().isBadRequest)
        }
        mockMvc.perform(replaceTask(taskId, """{"definition":${taskDefinitionJson(sourceId)}}""")).andExpect(status().isBadRequest)
    }

    private fun task(
        id: UUID,
        name: String = "Market headlines",
    ): CollectionTask = CollectionTask.create(id, definition(sourceId, name), createdAt)

    private fun definition(
        sourceId: UUID,
        name: String = "Market headlines",
        resource: String = "/articles",
    ) = TaskDefinition(
        name,
        sourceId,
        CollectionTarget(resource, linkedMapOf("topic" to "경제", "language" to "ko")),
        IntervalSchedule(3600, Instant.parse("2026-01-01T00:00:00Z")),
    )

    private fun taskDefinitionJson(
        sourceId: UUID,
        name: String = "Market headlines",
        resource: String = "/articles",
    ) = """
        {"name":"$name","sourceId":"$sourceId",
         "target":{"resource":"$resource","parameters":{"topic":"경제","language":"ko"}},
         "schedule":{"everySeconds":3600,"anchorAt":"2026-01-01T00:00:00Z"}}
        """.trimIndent()

    private fun taskJson(
        id: UUID,
        enabled: Boolean,
        version: Long,
        nextRunAt: String?,
    ) = """
        {"id":"$id","definition":${taskDefinitionJson(sourceId)},"enabled":$enabled,"version":$version,"createdAt":"$createdAt",
         "nextRunAt":${nextRunAt?.let { "\"$it\"" }}}
        """.trimIndent()

    private fun postTask(body: String) = post("/api/collection-tasks").authorized().contentType(MediaType.APPLICATION_JSON).content(body)

    private fun replaceTask(
        id: UUID,
        body: String,
    ) = put("/api/collection-tasks/{id}", id).authorized().contentType(MediaType.APPLICATION_JSON).content(body)

    private fun enableTask(
        id: UUID,
        expectedVersion: Long,
    ) = patch("/api/collection-tasks/{id}/enabled", id)
        .authorized()
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"expectedVersion":$expectedVersion,"enabled":true}""")
}
