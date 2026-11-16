package com.example.template.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.crypto.factory.PasswordEncoderFactories

class AdminPasswordTest {
    private val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Test
    fun `a bcrypt value is kept`() {
        assertThat(storedPassword("{bcrypt}abc", encoder)).isEqualTo("{bcrypt}abc")
    }

    @ParameterizedTest
    @ValueSource(strings = ["plain", "{}x", "{foo}bar", "{BCRYPT}x"])
    fun `every other value is encoded as plaintext`(raw: String) {
        val stored = storedPassword(raw, encoder)

        assertThat(stored).isNotEqualTo(raw)
        assertThat(encoder.matches(raw, stored)).isTrue()
    }
}
