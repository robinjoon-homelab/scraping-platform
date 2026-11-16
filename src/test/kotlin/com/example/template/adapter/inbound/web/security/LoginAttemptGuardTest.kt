package com.example.template.adapter.inbound.web.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private const val MAX_FAILURES = 5
private val lockDuration: Duration = Duration.ofMinutes(10)

class LoginAttemptGuardTest {
    private val clock = MovableClock(Instant.parse("2026-11-09T00:00:00Z"))
    private val guard = LoginAttemptGuard(MAX_FAILURES, lockDuration, clock)

    @Test
    fun `failures below the limit do not lock`() {
        fail(MAX_FAILURES - 1)

        assertThat(guard.isLocked()).isFalse()
    }

    @Test
    fun `reaching the limit locks`() {
        fail(MAX_FAILURES)

        assertThat(guard.isLocked()).isTrue()
    }

    @Test
    fun `the lock holds until the duration has passed`() {
        fail(MAX_FAILURES)

        clock.advance(lockDuration.minusSeconds(1))
        assertThat(guard.isLocked()).isTrue()

        clock.advance(Duration.ofSeconds(1))
        assertThat(guard.isLocked()).isFalse()
    }

    @Test
    fun `an expired lock starts counting from zero`() {
        fail(MAX_FAILURES)
        clock.advance(lockDuration)
        assertThat(guard.isLocked()).isFalse()

        fail(MAX_FAILURES - 1)

        assertThat(guard.isLocked()).isFalse()
    }

    @Test
    fun `success clears the accumulated failures`() {
        fail(MAX_FAILURES - 1)

        guard.recordSuccess()
        fail(MAX_FAILURES - 1)

        assertThat(guard.isLocked()).isFalse()
    }

    @Test
    fun `failures while locked do not extend the lock`() {
        fail(MAX_FAILURES)

        clock.advance(lockDuration.minusMinutes(1))
        guard.recordFailure()
        clock.advance(Duration.ofMinutes(1))

        assertThat(guard.isLocked()).isFalse()
    }

    private fun fail(times: Int) = repeat(times) { guard.recordFailure() }

    private class MovableClock(
        private var current: Instant,
    ) : Clock() {
        override fun instant(): Instant = current

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
