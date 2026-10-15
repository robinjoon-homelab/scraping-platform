package com.example.template.collection.adapter.outbound.http

import java.time.Duration

// responseTimeout is the deadline of a whole collection: the address check, connecting, the headers and the last body
// byte. connectTimeout bounds each connection attempt within it. maxBodyBytes stops reading as soon as the body grows
// past it.
data class HttpLimits(
    val connectTimeout: Duration,
    val responseTimeout: Duration,
    val maxBodyBytes: Int,
) {
    init {
        require(connectTimeout.isPositive) { "connect timeout must be positive" }
        require(responseTimeout.isPositive) { "response timeout must be positive" }
        require(maxBodyBytes > 0) { "maximum body size must be positive" }
    }
}
