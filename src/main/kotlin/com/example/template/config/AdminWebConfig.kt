package com.example.template.config

import com.example.template.adapter.inbound.web.WebSecurityHeadersFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
class AdminWebConfig : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController(ADMIN_LOGIN_PATH).setViewName("app/login")
        registry.addRedirectViewController("/admin", "/admin/todos")
    }

    // Runs before the security chain, so that its 302 and 403 responses carry the headers too.
    @Bean
    fun webSecurityHeadersFilterRegistration(): FilterRegistrationBean<WebSecurityHeadersFilter> =
        FilterRegistrationBean(WebSecurityHeadersFilter()).apply { order = Ordered.HIGHEST_PRECEDENCE }
}
