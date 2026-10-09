package com.example.template.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("scraping.security")
class ApiSecurityProperties(
    val apiToken: String = "",
)
