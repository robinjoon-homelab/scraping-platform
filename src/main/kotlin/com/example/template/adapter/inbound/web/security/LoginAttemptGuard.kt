package com.example.template.adapter.inbound.web.security

import java.time.Clock
import java.time.Duration
import java.time.Instant

// Counts for the whole application because there is only one operator account to protect.
class LoginAttemptGuard(
    private val maxFailures: Int,
    private val lockDuration: Duration,
    private val clock: Clock,
) {
    private var failures = 0
    private var lockedUntil: Instant? = null

    @Synchronized
    fun isLocked(): Boolean {
        val until = lockedUntil ?: return false
        if (clock.instant().isBefore(until)) {
            return true
        }
        clear()
        return false
    }

    @Synchronized
    fun recordFailure() {
        if (isLocked()) {
            return
        }
        failures += 1
        if (failures >= maxFailures) {
            lockedUntil = clock.instant().plus(lockDuration)
        }
    }

    @Synchronized
    fun recordSuccess() = clear()

    private fun clear() {
        failures = 0
        lockedUntil = null
    }
}
