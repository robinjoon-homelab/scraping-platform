package com.example.template.config

import org.springframework.boot.context.properties.ConfigurationProperties

internal const val BCRYPT_PREFIX = "{bcrypt}"

private const val BCRYPT_MAX_BYTES = 72

@ConfigurationProperties("app.admin")
class AdminSecurityProperties(
    val username: String = "",
    val password: String = "",
) {
    val enabled: Boolean
        get() = closedReason == null

    // Decided here so that the user store and the chain close together. It is logged, so it must never carry the value or its length.
    val closedReason: String?
        get() =
            when {
                username.isBlank() || password.isBlank() -> "ADMIN_USERNAME or ADMIN_PASSWORD is not set"
                isPlaintextTooLong -> "ADMIN_PASSWORD is too long for BCrypt; store a {bcrypt} hash instead"
                else -> null
            }

    // BCrypt rejects longer input with an exception, which would stop the whole application at boot.
    private val isPlaintextTooLong: Boolean
        get() = !password.startsWith(BCRYPT_PREFIX) && password.toByteArray(Charsets.UTF_8).size > BCRYPT_MAX_BYTES
}
