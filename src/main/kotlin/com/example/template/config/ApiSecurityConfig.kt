package com.example.template.config

import com.example.template.adapter.inbound.web.OperatorTokenFilter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiSecurityProperties::class)
class ApiSecurityConfig {
    @Bean
    fun operatorTokenFilter(properties: ApiSecurityProperties): OperatorTokenFilter = OperatorTokenFilter(properties.apiToken)
}
