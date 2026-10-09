package com.example.template.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=jdbc:h2:mem:auth-routing-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
class AuthRoutingRegressionTest {
    @LocalServerPort
    private var port = 0

    @Test
    fun `real server protects persisted sources across normalized and matrix routes`() {
        val sourceName = "protected-${UUID.randomUUID()}"
        HttpClient.newHttpClient().use { client ->
            createSource(client, sourceName)
            val paths = listOf("/api/sources", "/api;v=1/sources", "/%61pi/sources", "/api/sources;v=1")

            paths.forEach { path ->
                val authorized = get(client, path, "Bearer test-operator-token")
                assertThat(authorized.statusCode()).describedAs("authenticated path %s", path).isEqualTo(200)
                assertThat(authorized.body()).contains(sourceName)

                val unauthorized = get(client, path, null)
                assertThat(unauthorized.statusCode()).describedAs("unauthenticated path %s", path).isEqualTo(401)
                assertThat(unauthorized.body()).doesNotContain(sourceName)
            }
        }
    }

    @Test
    fun `alternate raw paths cannot disclose persisted sources without authorization`() {
        val sourceName = "protected-${UUID.randomUUID()}"
        HttpClient.newHttpClient().use { client ->
            createSource(client, sourceName)
            val paths = listOf("/docs/../api/sources", "//api/sources", "/api%3bv=1/sources", "/api%2Fsources")

            paths.forEach { path ->
                val response = get(client, path, null)

                assertThat(response.statusCode()).describedAs("unauthenticated path %s", path).isIn(400, 401, 404)
                assertThat(response.body()).doesNotContain(sourceName)
            }
        }
    }

    private fun createSource(
        client: HttpClient,
        sourceName: String,
    ) {
        val body = """{"name":"$sourceName","kind":"HTTP_JSON","baseUrl":"https://example.org"}"""
        val request =
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/sources"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer test-operator-token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201)
    }

    private fun get(
        client: HttpClient,
        path: String,
        authorization: String?,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(URI("http://localhost:$port$path"))
                .timeout(Duration.ofSeconds(10))
                .GET()
        if (authorization != null) request.header("Authorization", authorization)
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }
}
