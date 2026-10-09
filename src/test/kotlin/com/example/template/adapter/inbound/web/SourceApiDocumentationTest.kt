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
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
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
class SourceApiDocumentationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dataSource: DataSource

    @BeforeEach
    fun clearDatabase() {
        dataSource.clearConfiguration()
    }

    @Test
    fun `missing credentials reject source reads`() {
        mockMvc
            .perform(get("/api/sources"))
            .andExpect(status().isUnauthorized)
        dataSource.assertConfigurationCount("sources", 0)
    }

    @Test
    fun `invalid credentials reject source creation without persistence`() {
        mockMvc
            .perform(
                post("/api/sources")
                    .header("Authorization", "Bearer incorrect-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(sourceRequest()),
            ).andExpect(status().isUnauthorized)
        dataSource.assertConfigurationCount("sources", 0)
    }

    @Test
    fun `create persists a source with a normalized name and returns its location`() {
        val result =
            mockMvc
                .perform(
                    post("/api/sources")
                        .authorized()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"name":"  Economic data  ","kind":"HTTP_JSON","baseUrl":"https://api.example.org/v1","credentialRef":"economic-data"}""",
                        ),
                ).andExpect(status().isCreated)
                .andExpect(header().string("Location", startsWith("/api/sources/")))
                .andExpect(jsonPath("$.id").isNotEmpty)
                .andExpect(jsonPath("$.definition.name").value("Economic data"))
                .andExpect(jsonPath("$.definition.kind").value("HTTP_JSON"))
                .andExpect(jsonPath("$.definition.baseUrl").value("https://api.example.org/v1"))
                .andExpect(jsonPath("$.definition.credentialRef").value("economic-data"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty)
                .andDo(
                    documented(
                        "sources-create",
                        requestFields(*sourceDefinitionFields()),
                        responseHeaders(headerWithName("Location").description("생성된 데이터 출처의 조회 경로")),
                        responseFields(*sourceFields()),
                    ),
                ).andReturn()

        val location = checkNotNull(result.response.getHeader("Location"))
        val id = UUID.fromString(location.substringAfterLast('/'))
        dataSource.assertConfigurationCount("sources", 1)
        mockMvc
            .perform(get("/api/sources/{id}", id).authorized())
            .andExpect(status().isOk)
            .andExpect(content().json(result.response.contentAsString))
    }

    @Test
    fun `get returns the persisted source`() {
        val id = mockMvc.createSource("Technology feed")

        mockMvc
            .perform(get("/api/sources/{id}", id).authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.definition.name").value("Technology feed"))
            .andDo(documented("sources-get", resourceIdParameter(), responseFields(*sourceFields())))
    }

    @Test
    fun `list applies the requested offset and limit`() {
        mockMvc.createSource("First feed")
        val secondId = mockMvc.createSource("Second feed")
        mockMvc.createSource("Third feed")

        mockMvc
            .perform(get("/api/sources").authorized().queryParam("offset", "1").queryParam("limit", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(secondId.toString()))
            .andExpect(jsonPath("$[0].definition.name").value("Second feed"))
            .andDo(documented("sources-list", paginationParameters(), responseFields(*sourceFields("[]."))))
    }

    @Test
    fun `list returns an empty array when no sources exist`() {
        mockMvc
            .perform(get("/api/sources").authorized())
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `blank name returns a documented configuration problem`() {
        mockMvc
            .perform(
                post("/api/sources").authorized().contentType(MediaType.APPLICATION_JSON).content(sourceRequest(name = "   ")),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andDo(documented("configuration-invalid", responseFields(*problemFields())))
        dataSource.assertConfigurationCount("sources", 0)
    }

    @Test
    fun `invalid names kinds and missing fields are rejected without persistence`() {
        val requests =
            listOf(
                sourceRequest(name = ""),
                sourceRequest(name = "\u00a0"),
                sourceRequest(name = "x".repeat(121)),
                sourceRequest(kind = "UNKNOWN"),
                "{}",
                """{"name":null,"kind":"RSS_ATOM","baseUrl":"https://example.org"}""",
                """{"name":"Feed","baseUrl":"https://example.org"}""",
                """{"name":"Feed","kind":"RSS_ATOM"}""",
            )

        requests.forEach(::assertInvalidSource)
        dataSource.assertConfigurationCount("sources", 0)
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
        dataSource.assertConfigurationCount("sources", 0)
    }

    @Test
    fun `invalid credential references are rejected`() {
        val references = listOf("", "has space", "secret/token", "x".repeat(121))

        references.forEach { reference ->
            assertInvalidSource(
                """{"name":"Feed","kind":"RSS_ATOM","baseUrl":"https://example.org","credentialRef":"$reference"}""",
            )
        }
        dataSource.assertConfigurationCount("sources", 0)
    }

    @Test
    fun `a name at the 120 character boundary is accepted`() {
        val name = "x".repeat(120)

        mockMvc
            .perform(post("/api/sources").authorized().contentType(MediaType.APPLICATION_JSON).content(sourceRequest(name)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.definition.name").value(name))
        dataSource.assertConfigurationCount("sources", 1)
    }

    @Test
    fun `invalid pagination returns a configuration problem`() {
        val pagination = listOf("offset" to "-1", "limit" to "0", "limit" to "101", "limit" to "invalid")

        pagination.forEach { (parameter, value) ->
            mockMvc
                .perform(get("/api/sources").authorized().queryParam(parameter, value))
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
        }
    }

    @Test
    fun `get missing source returns a documented not found`() {
        mockMvc
            .perform(get("/api/sources/{id}", UUID.fromString("00000000-0000-0000-0000-000000000000")).authorized())
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andDo(documented("configuration-not-found", resourceIdParameter(), responseFields(*problemFields())))
    }

    @Test
    fun `malformed UUID and JSON return bad request`() {
        mockMvc
            .perform(get("/api/sources/{id}", "invalid-id").authorized())
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
        assertInvalidSource("{")
        dataSource.assertConfigurationCount("sources", 0)
    }

    private fun assertInvalidSource(request: String) {
        mockMvc
            .perform(post("/api/sources").authorized().contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
    }

    private fun sourceRequest(
        name: String = "Sample feed",
        baseUrl: String = "https://example.org",
        kind: String = "RSS_ATOM",
    ): String = """{"name":"$name","kind":"$kind","baseUrl":"$baseUrl"}"""
}
