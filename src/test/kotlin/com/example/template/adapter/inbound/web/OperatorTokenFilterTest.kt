package com.example.template.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class OperatorTokenFilterTest {
    @Test
    fun `missing wrong and malformed credentials cannot reach the API`() {
        val headers = listOf(null, "Bearer wrong", "Basic test-operator-token", "Bearer ", "Bearer test-operator-token extra")

        headers.forEach { authorization ->
            val request = request("/api/sources", authorization)
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()

            OperatorTokenFilter("test-operator-token").doFilter(request, response, chain)

            assertThat(response.status).isEqualTo(401)
            assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer realm=\"scraping-platform\"")
            assertThat(response.contentType).startsWith("application/problem+json")
            assertThat(response.contentAsString).doesNotContain("test-operator-token")
            assertThat(chain.request).isNull()
        }
    }

    @Test
    fun `the configured token authorizes the request without browser cookies`() {
        val request = request("/api/collection-tasks", "bearer test-operator-token")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        OperatorTokenFilter("test-operator-token").doFilter(request, response, chain)

        assertThat(chain.request).isSameAs(request)
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store")
    }

    @Test
    fun `unconfigured and whitespace tokens keep the API closed`() {
        listOf("", "   ").forEach { token ->
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()

            OperatorTokenFilter(token).doFilter(request("/api/sources", "Bearer $token"), response, chain)

            assertThat(response.status).isEqualTo(503)
            assertThat(chain.request).isNull()
        }
    }

    @Test
    fun `health and generated documentation remain public without a token`() {
        listOf("/actuator/health", "/actuator/health/readiness", "/docs/index.html").forEach { path ->
            val request = request(path, null)
            val chain = MockFilterChain()

            OperatorTokenFilter("").doFilter(request, MockHttpServletResponse(), chain)

            assertThat(chain.request).isSameAs(request)
        }
    }

    @Test
    fun `servlet path rather than raw URL determines authentication after container normalization`() {
        val request = request("/docs/../api/sources", null)
        request.servletPath = "/api/sources"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        OperatorTokenFilter("test-operator-token").doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(401)
        assertThat(chain.request).isNull()
    }

    @Test
    fun `context paths do not bypass token validation`() {
        val request = request("/platform/api/sources", null)
        request.contextPath = "/platform"
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        OperatorTokenFilter("test-operator-token").doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(401)
        assertThat(chain.request).isNull()
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
