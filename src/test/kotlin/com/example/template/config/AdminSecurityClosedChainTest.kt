package com.example.template.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.context.WebApplicationContext

@SpringJUnitWebConfig(AdminSecurityProbeConfig::class)
class AdminSecurityClosedChainTest
    @Autowired
    constructor(
        context: WebApplicationContext,
    ) {
        private val mockMvc = securedMockMvc(context)

        @ParameterizedTest
        @ValueSource(strings = ["/admin/probe", "/docs/probe", "/admin/login"])
        fun `every protected path is forbidden without credentials`(path: String) {
            mockMvc.perform(get(path)).andExpect(status().isForbidden)
        }

        // MockMvc performs no error dispatch, so the reason is what tells sendError from setStatus.
        @Test
        fun `anonymous rejection reaches the error page`() {
            mockMvc
                .perform(get("/admin/probe"))
                .andExpect(status().isForbidden)
                .andExpect(status().reason("Forbidden"))
        }

        @Test
        fun `form login is forbidden without credentials`() {
            mockMvc
                .perform(formLogin("/admin/login").user("admin").password("fixture-password"))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `even an admin principal is forbidden without credentials`() {
            val admin = user("admin").authorities(SimpleGrantedAuthority(ADMIN_ROLE))

            mockMvc.perform(get("/admin/probe").with(admin)).andExpect(status().isForbidden)
        }

        @Test
        fun `api stays open without credentials`() {
            mockMvc.perform(get("/api/probe")).andExpect(status().isOk)
        }
    }

// A password BCrypt cannot hash must close the admin chain, not stop the context from starting.
@SpringJUnitWebConfig(AdminSecurityProbeConfig::class)
@TestPropertySource(properties = ["app.admin.username=admin", "app.admin.password=$OVER_LONG_PASSWORD"])
class AdminSecurityOverLongPasswordChainTest
    @Autowired
    constructor(
        context: WebApplicationContext,
    ) {
        private val mockMvc = securedMockMvc(context)

        @ParameterizedTest
        @ValueSource(strings = ["/admin/probe", "/docs/probe", "/admin/login"])
        fun `every protected path is forbidden`(path: String) {
            mockMvc.perform(get(path)).andExpect(status().isForbidden)
        }

        @Test
        fun `api stays open`() {
            mockMvc.perform(get("/api/probe")).andExpect(status().isOk)
        }
    }

private const val OVER_LONG_PASSWORD = "0123456789012345678901234567890123456789012345678901234567890123456789012"
