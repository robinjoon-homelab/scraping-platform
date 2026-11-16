package com.example.template.adapter.inbound.web.security

import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder

class GuardedAuthenticationProvider(
    users: UserDetailsService,
    encoder: PasswordEncoder,
    private val guard: LoginAttemptGuard,
) : DaoAuthenticationProvider(users) {
    init {
        setPasswordEncoder(encoder)
    }

    // LockedException is an account status failure, so ProviderManager rethrows it
    // instead of falling through to its unguarded parent.
    override fun authenticate(authentication: Authentication): Authentication {
        if (guard.isLocked()) {
            throw LockedException("Too many failed login attempts")
        }
        return super.authenticate(authentication)
    }
}
