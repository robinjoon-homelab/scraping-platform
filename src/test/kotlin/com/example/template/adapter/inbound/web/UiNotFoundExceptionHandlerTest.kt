package com.example.template.adapter.inbound.web

import com.example.template.support.web.pageMockMvc
import jakarta.servlet.http.HttpServletRequest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.resource.NoResourceFoundException

private const val BROWSER_ACCEPT = "text/html,application/xhtml+xml,*/*;q=0.8"
private const val NOT_FOUND_MESSAGE = "페이지를 찾을 수 없습니다"

@RestController
private class MissingResourceController {
    @GetMapping("/**")
    fun missing(request: HttpServletRequest): Nothing =
        throw NoResourceFoundException(HttpMethod.GET, request.requestURI, request.requestURI.removePrefix("/"))
}

class UiNotFoundExceptionHandlerTest {
    private val mockMvc: MockMvc = pageMockMvc(MissingResourceController(), UiNotFoundExceptionHandler())

    @Test
    fun `browser gets the not found page`() {
        mockMvc
            .perform(get("/nope").header("Accept", BROWSER_ACCEPT))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString(NOT_FOUND_MESSAGE)))
    }

    @Test
    fun `wildcard client keeps problem json`() {
        mockMvc.perform(get("/nope").header("Accept", "*/*")).expectProblem("/nope")
    }

    @Test
    fun `json client keeps problem json`() {
        mockMvc.perform(get("/nope").header("Accept", MediaType.APPLICATION_JSON_VALUE)).expectProblem("/nope")
    }

    @Test
    fun `api path keeps problem json for browsers`() {
        mockMvc.perform(get("/api/nope").header("Accept", BROWSER_ACCEPT)).expectProblem("/api/nope")
    }

    // A handler that throws falls through to sendError, so an empty reason is what shows that it answered.
    @Test
    fun `malformed accept is treated as non html`() {
        mockMvc
            .perform(get("/nope").header("Accept", "not a media type"))
            .andExpect(status().isNotFound)
            .andExpect(status().reason(nullValue()))
            .andExpect(content().string(not(containsString(NOT_FOUND_MESSAGE))))
    }

    private fun ResultActions.expectProblem(instance: String) {
        andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.instance").value(instance))
    }
}
