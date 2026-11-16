package com.example.template.adapter.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

private const val ADMIN_ROOT = "/admin"
private const val DOCS_ROOT = "/docs"
private val apiRoots = listOf("/api", "/actuator")

private const val CONTENT_SECURITY_POLICY =
    "default-src 'none'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; " +
        "script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'"

private val browserHeaders =
    mapOf(
        "X-Content-Type-Options" to "nosniff",
        "X-Frame-Options" to "DENY",
        "Referrer-Policy" to "same-origin",
        "Permissions-Policy" to "accelerometer=(), camera=(), geolocation=(), microphone=(), payment=(), usb=()",
    )

class WebSecurityHeadersFilter : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = applicationPath(request)
        return apiRoots.any { isUnder(path, it) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = applicationPath(request)
        browserHeaders.forEach(response::setHeader)
        // Asciidoctor output relies on inline styles, which this policy would block.
        if (!isUnder(path, DOCS_ROOT)) {
            response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY)
        }
        if (isUnder(path, ADMIN_ROOT)) {
            response.setHeader("Cache-Control", "no-store")
            response.setHeader("Pragma", "no-cache")
        }
        filterChain.doFilter(request, response)
    }

    private fun applicationPath(request: HttpServletRequest): String = request.requestURI.removePrefix(request.contextPath)

    private fun isUnder(
        path: String,
        root: String,
    ): Boolean = path == root || path.startsWith("$root/")
}
