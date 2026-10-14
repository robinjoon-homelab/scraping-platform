package com.example.template.collection.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class OperatorTokenFilterTest {
    @Test
    fun `missing wrong and malformed credentials are rejected as unauthorized`() {
        val headers = listOf(null, "Bearer wrong", "Basic test-operator-token", "Bearer ", "Bearer test-operator-token extra")

        headers.forEach { authorization ->
            val request = request("/api/sources", authorization)
            val response = MockHttpServletResponse()

            OperatorTokenFilter("test-operator-token").doFilter(request, response, MockFilterChain())

            assertThat(response.status).isEqualTo(401)
            assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer realm=\"scraping-platform\"")
            assertThat(response.contentType).startsWith("application/problem+json")
            assertThat(response.contentAsString).doesNotContain("test-operator-token")
        }
    }

    @Test
    fun `the configured token is accepted without browser cookies`() {
        val request = request("/api/collection-tasks", "bearer test-operator-token")
        val response = MockHttpServletResponse()

        OperatorTokenFilter("test-operator-token").doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(200)
        assertThat(response.contentAsString).isEmpty()
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store")
    }

    @Test
    fun `unconfigured and whitespace tokens keep the API closed`() {
        listOf("", "   ").forEach { token ->
            val response = MockHttpServletResponse()

            OperatorTokenFilter(token).doFilter(request("/api/sources", "Bearer $token"), response, MockFilterChain())

            assertThat(response.status).isEqualTo(503)
        }
    }

    @Test
    fun `health and generated documentation remain public without a token`() {
        listOf("/actuator/health", "/actuator/health/readiness", "/docs/index.html").forEach { path ->
            val response = MockHttpServletResponse()

            OperatorTokenFilter("").doFilter(request(path, null), response, MockFilterChain())

            assertThat(response.status).describedAs(path).isEqualTo(200)
            assertThat(response.contentAsString).describedAs(path).isEmpty()
        }
    }

    @Test
    fun `servlet path rather than raw URL determines authentication after container normalization`() {
        val request = request("/docs/../api/sources", null)
        request.servletPath = "/api/sources"
        val response = MockHttpServletResponse()

        OperatorTokenFilter("test-operator-token").doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `context paths do not bypass token validation`() {
        val request = request("/platform/api/sources", null)
        request.contextPath = "/platform"
        val response = MockHttpServletResponse()

        OperatorTokenFilter("test-operator-token").doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(401)
    }

    private fun request(
        path: String,
        authorization: String?,
    ): MockHttpServletRequest {
        val request = MockHttpServletRequest("GET", path)
        if (authorization != null) {
            request.addHeader("Authorization", authorization)
        }
        return request
    }
}
