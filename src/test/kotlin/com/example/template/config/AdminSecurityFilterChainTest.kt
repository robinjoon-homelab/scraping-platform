package com.example.template.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.time.Clock

@SpringJUnitWebConfig(AdminSecurityProbeConfig::class)
@TestPropertySource(properties = ["app.admin.username=admin", "app.admin.password=fixture-password"])
class AdminSecurityFilterChainTest
    @Autowired
    constructor(
        context: WebApplicationContext,
    ) {
        private val mockMvc = securedMockMvc(context)

        @Test
        fun `api needs neither login nor csrf`() {
            mockMvc.perform(get("/api/probe")).andExpect(status().isOk)
            mockMvc.perform(post("/api/probe")).andExpect(status().isOk)
        }

        @ParameterizedTest
        @ValueSource(strings = ["/admin/probe", "/docs/probe"])
        fun `anonymous admin and docs go to login`(path: String) {
            mockMvc
                .perform(get(path))
                .andExpect(status().isFound)
                .andExpect(redirectedUrl("/admin/login"))
        }

        @ParameterizedTest
        @ValueSource(strings = ["/admin/login", "/admin/login?error", "/admin/login?locked", "/admin/login?logout"])
        fun `login page is open in every state`(url: String) {
            mockMvc.perform(get(url)).andExpect(status().isOk)
        }

        @Test
        fun `correct credentials open admin and docs`() {
            val session = loggedInSession()

            mockMvc.perform(get("/admin/probe").session(session)).andExpect(status().isOk)
            mockMvc.perform(get("/docs/probe").session(session)).andExpect(status().isOk)
        }

        @ParameterizedTest
        @CsvSource("admin, wrong-password", "nobody, fixture-password")
        fun `wrong password and unknown user fail the same way`(
            username: String,
            password: String,
        ) {
            mockMvc
                .perform(formLogin("/admin/login").user(username).password(password))
                .andExpect(status().isFound)
                .andExpect(redirectedUrl("/admin/login?error"))
        }

        @Test
        fun `login needs csrf`() {
            mockMvc
                .perform(post("/admin/login").param("username", "admin").param("password", "fixture-password"))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `admin writes need csrf`() {
            val session = loggedInSession()

            mockMvc.perform(post("/admin/probe").session(session)).andExpect(status().isForbidden)
            mockMvc.perform(post("/admin/probe").session(session).with(csrf())).andExpect(status().isOk)
        }

        @ParameterizedTest
        @ValueSource(strings = ["/admin/login", "/admin/probe", "/admin/logout"])
        fun `a stale session without a token goes to login`(path: String) {
            mockMvc
                .perform(post(path).with(requestedSession("expired-session-id", valid = false)))
                .andExpect(status().isFound)
                .andExpect(redirectedUrl("/admin/login"))
        }

        @Test
        fun `a live session without a token stays forbidden`() {
            val session = loggedInSession()

            mockMvc
                .perform(post("/admin/probe").session(session).with(requestedSession(session.id, valid = true)))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `a request without a session cookie or a token stays forbidden`() {
            mockMvc.perform(post("/admin/probe")).andExpect(status().isForbidden)
        }

        private fun requestedSession(
            id: String,
            valid: Boolean,
        ) = RequestPostProcessor { request ->
            request.requestedSessionId = id
            request.isRequestedSessionIdValid = valid
            request
        }

        private fun loggedInSession(): MockHttpSession =
            mockMvc
                .perform(formLogin("/admin/login").user("admin").password("fixture-password"))
                .andExpect(status().isFound)
                .andExpect(redirectedUrl("/admin"))
                .andReturn()
                .request
                .session as MockHttpSession
    }

internal fun securedMockMvc(context: WebApplicationContext): MockMvc =
    MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()

@Configuration(proxyBeanMethods = false)
@EnableWebMvc
@EnableWebSecurity
@Import(AdminSecurityConfig::class)
class AdminSecurityProbeConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun adminSecurityProbeController(): AdminSecurityProbeController = AdminSecurityProbeController()
}

@RestController
class AdminSecurityProbeController {
    @GetMapping("/admin/probe", "/admin/login", "/docs/probe", "/api/probe")
    fun read(): String = "ok"

    @PostMapping("/admin/probe", "/api/probe")
    fun write(): String = "ok"
}
