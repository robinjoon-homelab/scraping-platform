package com.example.template.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AdminSecurityPropertiesTest {
    private fun enabled(password: String): Boolean = AdminSecurityProperties("admin", password).enabled

    @Test
    fun `plaintext at the bcrypt limit is enabled`() {
        assertThat(enabled("a".repeat(72))).isTrue()
    }

    @Test
    fun `plaintext one byte over the bcrypt limit is closed`() {
        assertThat(enabled("a".repeat(73))).isFalse()
    }

    @Test
    fun `the limit counts utf-8 bytes rather than characters`() {
        assertThat(enabled("가".repeat(25))).isFalse()
    }

    @Test
    fun `a long bcrypt value is not subject to the limit`() {
        assertThat(enabled("{bcrypt}" + "a".repeat(200))).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["{BCRYPT}", "{foo}", "{}"])
    fun `another prefix is still plaintext and stays subject to the limit`(prefix: String) {
        assertThat(enabled(prefix + "a".repeat(73))).isFalse()
    }

    @Test
    fun `blank credentials are closed`() {
        assertThat(AdminSecurityProperties().enabled).isFalse()
        assertThat(AdminSecurityProperties("admin", " ").enabled).isFalse()
        assertThat(AdminSecurityProperties(" ", "password").enabled).isFalse()
    }
}
