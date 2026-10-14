package com.example.template.collection.adapter.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

class OperatorTokenFilter(
    apiToken: String,
) : OncePerRequestFilter() {
    private val configured = apiToken.isNotBlank()
    private val expectedDigest = digest(apiToken)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath.ifEmpty { request.requestURI.removePrefix(request.contextPath) }
        return path != "/api" && !path.startsWith("/api/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        if (!configured) {
            reject(response, 503, "API access is not configured")
            return
        }
        val authorization = request.getHeader(HttpHeaders.AUTHORIZATION).orEmpty()
        if (!authorized(authorization)) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"scraping-platform\"")
            reject(response, 401, "A valid operator bearer token is required")
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun authorized(authorization: String): Boolean =
        authorization.startsWith("Bearer ", ignoreCase = true) &&
            MessageDigest.isEqual(expectedDigest, digest(authorization.substring(7)))

    private fun reject(
        response: HttpServletResponse,
        status: Int,
        detail: String,
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write("""{"type":"about:blank","title":"API access denied","status":$status,"detail":"$detail"}""")
    }

    private fun digest(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}
