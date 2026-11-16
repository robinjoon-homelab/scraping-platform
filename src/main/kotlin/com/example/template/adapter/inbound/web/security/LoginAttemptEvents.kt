package com.example.template.adapter.inbound.web.security

import org.springframework.context.event.EventListener
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent
import org.springframework.security.authentication.event.AuthenticationSuccessEvent

// Counts events, not provider calls: a failed login also reaches the parent ProviderManager,
// which retries it, while only one event is published per attempt.
class LoginAttemptEvents(
    private val guard: LoginAttemptGuard,
) {
    @EventListener(AuthenticationSuccessEvent::class)
    fun onSuccess() = guard.recordSuccess()

    @EventListener(AuthenticationFailureBadCredentialsEvent::class)
    fun onBadCredentials() = guard.recordFailure()
}
