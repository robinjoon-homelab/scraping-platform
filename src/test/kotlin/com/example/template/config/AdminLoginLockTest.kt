package com.example.template.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.web.context.WebApplicationContext

// The username separates this cached context, so that the lock cannot leak into other tests.
@SpringJUnitWebConfig(AdminSecurityProbeConfig::class)
@TestPropertySource(properties = ["app.admin.username=lock-admin", "app.admin.password=fixture-password"])
class AdminLoginLockTest
    @Autowired
    constructor(
        context: WebApplicationContext,
    ) {
        private val mockMvc = securedMockMvc(context)

        @Test
        fun `five wrong passwords lock out even the correct one`() {
            repeat(MAX_FAILURES) {
                login("wrong-password").andExpect(redirectedUrl("/admin/login?error"))
            }

            login("fixture-password").andExpect(redirectedUrl("/admin/login?locked"))
        }

        private fun login(password: String): ResultActions =
            mockMvc.perform(formLogin("/admin/login").user("lock-admin").password(password))

        private companion object {
            const val MAX_FAILURES = 5
        }
    }
