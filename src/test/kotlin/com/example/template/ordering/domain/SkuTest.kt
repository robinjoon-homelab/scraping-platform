package com.example.template.ordering.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SkuTest {
    @Test
    fun `sku creation trims the customer input`() {
        assertThat(Sku.of("  KB-001 \t").value).isEqualTo("KB-001")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n", " "])
    fun `blank skus are rejected`(raw: String) {
        assertThatThrownBy { Sku.of(raw) }
            .isInstanceOf(InvalidSkuException::class.java)
            .hasMessage("sku must not be blank")
    }

    @Test
    fun `a sku with exactly 64 characters is allowed and a longer one is rejected`() {
        assertThat(Sku.of("A".repeat(64)).value).hasSize(64)
        assertThatThrownBy { Sku.of("A".repeat(65)) }
            .isInstanceOf(InvalidSkuException::class.java)
            .hasMessage("sku must be at most 64 characters")
    }

    @Test
    fun `length is checked after trimming`() {
        assertThat(Sku.of(" ${"A".repeat(64)} ").value).hasSize(64)
    }

    @Test
    fun `a stored sku must already be in canonical form`() {
        assertThatThrownBy { Sku(" KB-001") }
            .isInstanceOf(InvalidSkuException::class.java)
            .hasMessage("sku must not start or end with whitespace")
    }

    @Test
    fun `characters that are not safe in a url path are still a valid ordering sku`() {
        assertThat(Sku.of("BOOK 001/A").value).isEqualTo("BOOK 001/A")
    }
}
