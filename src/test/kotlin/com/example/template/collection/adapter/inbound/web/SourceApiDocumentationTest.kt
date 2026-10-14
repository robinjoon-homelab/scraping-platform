package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.input.SourceUseCase
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.support.web.RestDocsWebTest
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

// Name trimming and the stored values are covered by the domain and SourceServiceTest; this test checks how the
// endpoint turns requests, use case results and failures into HTTP responses.
class SourceApiDocumentationTest : RestDocsWebTest() {
    private val sourceId: UUID = UUID.fromString("b1e38ed1-1d8a-4078-9322-a17fa7e30ec8")
    private val secondSourceId: UUID = UUID.fromString("8a6e0f2d-1c3b-4d5e-8f70-9a1b2c3d4e5f")
    private val missingId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val createdAt: Instant = Instant.parse("2026-10-05T00:00:00.123456Z")
    private val sourceUseCase = mockk<SourceUseCase>()
    private val mockMvc = restDocsMockMvc(SourceController(sourceUseCase), ApiExceptionHandler(), operatorTokenFilter())

    @Test
    fun `missing credentials reject source reads`() {
        mockMvc
            .perform(get("/api/sources"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    @Test
    fun `invalid credentials reject source creation`() {
        mockMvc
            .perform(
                post("/api/sources")
                    .header("Authorization", "Bearer incorrect-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(sourceRequest()),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `create returns the created source with its location`() {
        val definition = SourceDefinition("  Economic data  ", SourceKind.HTTP_JSON, "https://api.example.org/v1", "economic-data")
        val created = Source.create(sourceId, definition, createdAt)
        every { sourceUseCase.create(definition) } returns created

        create("""{"name":"  Economic data  ","kind":"HTTP_JSON","baseUrl":"https://api.example.org/v1","credentialRef":"economic-data"}""")
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/sources/$sourceId"))
            .andExpect(jsonPath("$.definition.name").value("Economic data"))
            .andExpect(content().json(sourceJson(created), JsonCompareMode.STRICT))
            .andDo(
                documented(
                    "sources-create",
                    requestFields(*sourceDefinitionFields()),
                    responseHeaders(headerWithName("Location").description("생성된 데이터 출처의 조회 경로")),
                    responseFields(*sourceFields()),
                ),
            )
    }

    @Test
    fun `get returns the source of the path id`() {
        val source = source(sourceId, "Technology feed")
        every { sourceUseCase.get(sourceId) } returns source

        mockMvc
            .perform(get("/api/sources/{id}", sourceId).authorized())
            .andExpect(status().isOk)
            .andExpect(content().json(sourceJson(source), JsonCompareMode.STRICT))
            .andDo(documented("sources-get", resourceIdParameter(), responseFields(*sourceFields())))
    }

    @Test
    fun `list returns the requested page`() {
        every { sourceUseCase.list(1, 1) } returns listOf(source(secondSourceId, "Second feed"))

        mockMvc
            .perform(get("/api/sources").authorized().queryParam("offset", "1").queryParam("limit", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(secondSourceId.toString()))
            .andExpect(jsonPath("$[0].definition.name").value("Second feed"))
            .andDo(documented("sources-list", paginationParameters(), responseFields(*sourceFields("[]."))))
    }

    @Test
    fun `list uses the default page and returns an empty array when the use case gives no sources`() {
        every { sourceUseCase.list(0, 50) } returns emptyList()

        mockMvc
            .perform(get("/api/sources").authorized())
            .andExpect(status().isOk)
            .andExpect(content().json("[]", JsonCompareMode.STRICT))
    }

    @Test
    fun `blank name returns a documented configuration problem`() {
        create(sourceRequest(name = "   "))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Invalid collection configuration"))
            .andExpect(jsonPath("$.instance").value("/api/sources"))
            .andDo(documented("configuration-invalid", responseFields(*problemFields())))
    }

    @Test
    fun `invalid names kinds missing fields and malformed JSON are rejected`() {
        val requests =
            listOf(
                sourceRequest(name = ""),
                sourceRequest(name = " "),
                sourceRequest(name = "x".repeat(121)),
                sourceRequest(kind = "UNKNOWN"),
                "{}",
                "{",
                """{"name":null,"kind":"RSS_ATOM","baseUrl":"https://example.org"}""",
                """{"name":"Feed","baseUrl":"https://example.org"}""",
                """{"name":"Feed","kind":"RSS_ATOM"}""",
            )

        requests.forEach(::assertInvalidSource)
    }

    @Test
    fun `base URL must be a valid HTTP address without credentials query or fragment`() {
        val urls =
            listOf(
                "https://example.org/%",
                "https://example.org/a b",
                "ftp://example.org",
                "/relative/path",
                "https:///missing-host",
                "https://example.com:0",
                "https://example.com:65536",
                "https://user:password@example.org",
                "https://example.org?symbol=ABC",
                "https://example.org#section",
                "https://example.org/${"x".repeat(2048)}",
            )

        urls.forEach { url -> assertInvalidSource(sourceRequest(baseUrl = url)) }
    }

    @Test
    fun `invalid credential references are rejected`() {
        val references = listOf("", "has space", "secret/token", "x".repeat(121))

        references.forEach { reference ->
            assertInvalidSource("""{"name":"Feed","kind":"RSS_ATOM","baseUrl":"https://example.org","credentialRef":"$reference"}""")
        }
    }

    @Test
    fun `a name at the 120 character boundary is accepted`() {
        val name = "x".repeat(120)
        every { sourceUseCase.create(SourceDefinition(name, SourceKind.RSS_ATOM, "https://example.org")) } answers
            { Source.create(sourceId, firstArg(), createdAt) }

        create(sourceRequest(name))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.definition.name").value(name))
    }

    @Test
    fun `pagination rejected by the use case or malformed returns a configuration problem`() {
        every { sourceUseCase.list(-1, 50) } throws InvalidCollectionConfigurationException("Offset must not be negative")

        listOf("offset" to "-1", "limit" to "invalid").forEach { (parameter, value) ->
            mockMvc
                .perform(get("/api/sources").authorized().queryParam(parameter, value))
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
        }
    }

    @Test
    fun `get missing source returns a documented not found`() {
        every { sourceUseCase.get(missingId) } throws SourceNotFoundException(missingId)

        mockMvc
            .perform(get("/api/sources/{id}", missingId).authorized())
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Source not found"))
            .andExpect(jsonPath("$.instance").value("/api/sources/$missingId"))
            .andDo(documented("configuration-not-found", resourceIdParameter(), responseFields(*problemFields())))
    }

    @Test
    fun `malformed id returns a problem bad request`() {
        mockMvc
            .perform(get("/api/sources/{id}", "invalid-id").authorized())
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
    }

    private fun create(body: String): ResultActions =
        mockMvc.perform(post("/api/sources").authorized().contentType(MediaType.APPLICATION_JSON).content(body))

    private fun assertInvalidSource(request: String) {
        create(request)
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
    }

    private fun source(
        id: UUID,
        name: String,
    ): Source = Source.restore(id, SourceDefinition(name, SourceKind.HTTP_JSON, "https://example.com"), createdAt)

    private fun sourceRequest(
        name: String = "Sample feed",
        baseUrl: String = "https://example.org",
        kind: String = "RSS_ATOM",
    ): String = """{"name":"$name","kind":"$kind","baseUrl":"$baseUrl"}"""

    private fun sourceJson(source: Source): String {
        val definition = source.definition
        val reference = definition.credentialRef?.let { "\"$it\"" } ?: "null"
        return """
            {"id":"${source.id}","definition":{"name":"${definition.name}","kind":"${definition.kind}",
             "baseUrl":"${definition.baseUrl}","credentialRef":$reference},"createdAt":"${source.createdAt}"}
            """.trimIndent()
    }
}
