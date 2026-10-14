package com.example.template.collection.adapter.outbound.http

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

class HttpLimitsTest {
    private val second = Duration.ofSeconds(1)

    @Test
    fun `timeouts and the body limit must be positive`() {
        assertThatThrownBy { HttpLimits(Duration.ZERO, second, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("connect timeout must be positive")
        assertThatThrownBy { HttpLimits(second, second.negated(), 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("response timeout must be positive")
        assertThatThrownBy { HttpLimits(second, second, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("maximum body size must be positive")
    }
}
