package com.example.template.todo.adapter.inbound.web

import io.mockk.mockk
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ApiExceptionHandlerTest {
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(TodoController(mockk()))
            .setControllerAdvice(ApiExceptionHandler())
            .build()

    @ParameterizedTest
    @ValueSource(strings = [MediaType.APPLICATION_JSON_VALUE, "text/html,*/*;q=0.8"])
    fun `unsupported method answers with problem json for every client`(accept: String) {
        mockMvc
            .perform(delete("/api/todos").header("Accept", accept))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Method Not Allowed"))
    }
}
