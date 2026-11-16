package com.example.template.config

import com.example.template.adapter.inbound.web.security.GuardedAuthenticationProvider
import com.example.template.adapter.inbound.web.security.LoginAttemptEvents
import com.example.template.adapter.inbound.web.security.LoginAttemptGuard
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.LockedException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.DefaultRedirectStrategy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.access.AccessDeniedHandlerImpl
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import java.time.Clock
import java.time.Duration

internal const val ADMIN_ROLE = "ROLE_ADMIN"
internal const val ADMIN_LOGIN_PATH = "/admin/login"

private const val MAX_LOGIN_FAILURES = 5
private const val LOCK_MINUTES = 10L

private val log = LoggerFactory.getLogger(AdminSecurityConfig::class.java)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminSecurityProperties::class)
class AdminSecurityConfig(
    private val properties: AdminSecurityProperties,
) {
    @Bean
    fun adminPasswordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    // Stays registered without credentials, so that Spring Boot does not create its temporary account.
    @Bean
    fun adminUserDetailsService(encoder: PasswordEncoder): UserDetailsService {
        if (!properties.enabled) {
            log.warn("Admin pages are closed: {}", properties.closedReason)
            return InMemoryUserDetailsManager()
        }
        val admin =
            User
                .withUsername(properties.username)
                .password(storedPassword(properties.password, encoder))
                .authorities(ADMIN_ROLE)
                .build()
        return InMemoryUserDetailsManager(admin)
    }

    @Bean
    fun loginAttemptGuard(clock: Clock): LoginAttemptGuard = LoginAttemptGuard(MAX_LOGIN_FAILURES, Duration.ofMinutes(LOCK_MINUTES), clock)

    @Bean
    fun loginAttemptEvents(guard: LoginAttemptGuard): LoginAttemptEvents = LoginAttemptEvents(guard)

    @Bean
    fun adminSecurityFilterChain(
        http: HttpSecurity,
        users: UserDetailsService,
        encoder: PasswordEncoder,
        guard: LoginAttemptGuard,
    ): SecurityFilterChain {
        http.securityMatcher("/admin/**", "/docs/**")
        http.authorizeHttpRequests { requests ->
            if (properties.enabled) {
                requests.requestMatchers(ADMIN_LOGIN_PATH).permitAll()
                requests.requestMatchers("/admin/**", "/docs/**").hasAuthority(ADMIN_ROLE)
            }
            requests.anyRequest().denyAll()
        }
        // WebSecurityHeadersFilter owns the headers so that responses outside this chain get them too.
        http.headers { it.disable() }
        http.logout { it.logoutUrl("/admin/logout").logoutSuccessUrl("$ADMIN_LOGIN_PATH?logout") }
        if (properties.enabled) {
            configureLogin(http, GuardedAuthenticationProvider(users, encoder, guard))
        } else {
            // This entry point calls sendError, which reaches the error page; a bare status would leave the body empty.
            http.exceptionHandling { it.authenticationEntryPoint(Http403ForbiddenEntryPoint()) }
        }
        return http.build()
    }

    private fun configureLogin(
        http: HttpSecurity,
        provider: AuthenticationProvider,
    ) {
        http.authenticationProvider(provider)
        http.formLogin {
            it.loginPage(ADMIN_LOGIN_PATH)
            it.loginProcessingUrl(ADMIN_LOGIN_PATH)
            it.defaultSuccessUrl("/admin", false)
            it.failureHandler(redirectByFailure())
        }
        http.exceptionHandling { it.accessDeniedHandler(redirectStaleSessionToLogin()) }
    }

    // A form left open past the session timeout has lost its CSRF token and would otherwise end on a dead-end 403 page.
    private fun redirectStaleSessionToLogin(): AccessDeniedHandler {
        val redirects = DefaultRedirectStrategy()
        val denied = AccessDeniedHandlerImpl()
        return AccessDeniedHandler { request, response, exception ->
            if (request.requestedSessionId != null && !request.isRequestedSessionIdValid) {
                redirects.sendRedirect(request, response, ADMIN_LOGIN_PATH)
            } else {
                denied.handle(request, response, exception)
            }
        }
    }

    private fun redirectByFailure(): AuthenticationFailureHandler {
        val redirects = DefaultRedirectStrategy()
        return AuthenticationFailureHandler { request, response, failure ->
            val state = if (failure is LockedException) "locked" else "error"
            redirects.sendRedirect(request, response, "$ADMIN_LOGIN_PATH?$state")
        }
    }
}

// Only bcrypt counts as stored: another {id} value kept as is would fail every login when no encoder maps that id.
internal fun storedPassword(
    raw: String,
    encoder: PasswordEncoder,
): String =
    if (raw.startsWith(BCRYPT_PREFIX)) {
        raw
    } else {
        requireNotNull(encoder.encode(raw)) { "The password encoder returned no value." }
    }
