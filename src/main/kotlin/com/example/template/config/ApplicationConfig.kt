package com.example.template.config

import com.example.template.application.port.input.TodoUseCase
import com.example.template.application.port.output.TodoRepository
import com.example.template.application.service.TodoService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun todoUseCase(
        repository: TodoRepository,
        clock: Clock,
    ): TodoUseCase = TodoService(repository, clock)
}
