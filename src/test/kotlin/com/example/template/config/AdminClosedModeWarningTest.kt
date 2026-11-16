package com.example.template.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.security.crypto.factory.PasswordEncoderFactories

@ExtendWith(OutputCaptureExtension::class)
class AdminClosedModeWarningTest {
    private val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    private fun startWith(properties: AdminSecurityProperties) {
        AdminSecurityConfig(properties).adminUserDetailsService(encoder)
    }

    // Only the text after the logger name: a timestamp may contain any digits.
    private fun warnings(output: CapturedOutput): List<String> =
        output.all
            .lines()
            .filter { "WARN" in it && "AdminSecurityConfig" in it }
            .map { it.substringAfter("AdminSecurityConfig") }

    @Test
    fun `missing credentials warn once with the reason`(output: CapturedOutput) {
        startWith(AdminSecurityProperties())

        assertThat(warnings(output)).singleElement().asString().contains("not set")
    }

    @Test
    fun `an over-long password warns once without its value or length`(output: CapturedOutput) {
        val password = "x".repeat(73)

        startWith(AdminSecurityProperties("admin", password))

        assertThat(warnings(output))
            .singleElement()
            .asString()
            .contains("too long")
            .doesNotContain(password)
            .doesNotContain("73")
    }

    @Test
    fun `usable credentials do not warn`(output: CapturedOutput) {
        startWith(AdminSecurityProperties("admin", "fixture-password"))

        assertThat(warnings(output)).isEmpty()
    }
}
