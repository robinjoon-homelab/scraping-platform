package com.example.template.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

private const val CSP = "Content-Security-Policy"
private const val EXPECTED_CSP =
    "default-src 'none'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; " +
        "script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'"
private const val NOSNIFF = "X-Content-Type-Options"
private const val FRAME_OPTIONS = "X-Frame-Options"
private const val REFERRER_POLICY = "Referrer-Policy"
private const val PERMISSIONS_POLICY = "Permissions-Policy"
private const val EXPECTED_PERMISSIONS_POLICY =
    "accelerometer=(), camera=(), geolocation=(), microphone=(), payment=(), usb=()"
private const val CACHE_CONTROL = "Cache-Control"
private const val PRAGMA = "Pragma"

@RestController
private class AnyPathController {
    @GetMapping("/**")
    fun any(): String = "ok"
}

class WebSecurityHeadersFilterTest {
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(AnyPathController())
            .addFilters<StandaloneMockMvcBuilder>(WebSecurityHeadersFilter())
            .build()

    @ParameterizedTest
    @ValueSource(strings = ["/nope", "/ui/tokens.css", "/apiary"])
    fun `pages get every security header and stay cacheable`(path: String) {
        response(path).andExpectBrowserHeaders().andExpectCacheable()
    }

    @ParameterizedTest
    @ValueSource(strings = ["/admin", "/admin/x"])
    fun `admin responses are never stored`(path: String) {
        response(path)
            .andExpectBrowserHeaders()
            .andExpect(header().string(CACHE_CONTROL, "no-store"))
            .andExpect(header().string(PRAGMA, "no-cache"))
    }

    @Test
    fun `a sibling of admin stays cacheable`() {
        response("/administrator").andExpectCacheable()
    }

    @Test
    fun `generated docs get no csp because they use inline styles`() {
        response("/docs/index.html")
            .andExpect(header().doesNotExist(CSP))
            .andExpect(header().string(NOSNIFF, "nosniff"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api", "/api/x", "/actuator", "/actuator/health"])
    fun `api and actuator responses stay untouched`(path: String) {
        val headerNames = response(path).andReturn().response.headerNames

        assertThat(headerNames).doesNotContainAnyElementsOf(allHeaders)
    }

    @Test
    fun `paths are judged without the context path`() {
        val contextPath = "/context"

        val api = mockMvc.perform(get("$contextPath/api/x").contextPath(contextPath)).andReturn().response
        assertThat(api.headerNames).doesNotContainAnyElementsOf(allHeaders)

        mockMvc
            .perform(get("$contextPath/admin/x").contextPath(contextPath))
            .andExpect(header().string(CACHE_CONTROL, "no-store"))
    }

    private fun response(path: String): ResultActions = mockMvc.perform(get(path))

    private fun ResultActions.andExpectBrowserHeaders(): ResultActions =
        andExpect(header().string(CSP, EXPECTED_CSP))
            .andExpect(header().string(NOSNIFF, "nosniff"))
            .andExpect(header().string(FRAME_OPTIONS, "DENY"))
            .andExpect(header().string(REFERRER_POLICY, "same-origin"))
            .andExpect(header().string(PERMISSIONS_POLICY, EXPECTED_PERMISSIONS_POLICY))

    private fun ResultActions.andExpectCacheable(): ResultActions =
        andExpect(header().doesNotExist(CACHE_CONTROL)).andExpect(header().doesNotExist(PRAGMA))

    private companion object {
        val allHeaders = listOf(CSP, NOSNIFF, FRAME_OPTIONS, REFERRER_POLICY, PERMISSIONS_POLICY, CACHE_CONTROL, PRAGMA)
    }
}
