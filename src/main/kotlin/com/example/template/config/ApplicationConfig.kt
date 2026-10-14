package com.example.template.config

import com.example.template.collection.application.port.input.CollectionSchedulingUseCase
import com.example.template.collection.application.port.input.CollectionTaskUseCase
import com.example.template.collection.application.port.input.SourceUseCase
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.application.service.CollectionSchedulingService
import com.example.template.collection.application.service.CollectionSlotService
import com.example.template.collection.application.service.CollectionTaskService
import com.example.template.collection.application.service.SourceService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun sourceUseCase(
        repository: SourceRepository,
        clock: Clock,
    ): SourceUseCase = SourceService(repository, clock)

    @Bean
    fun collectionTaskUseCase(
        repository: CollectionTaskRepository,
        sources: SourceRepository,
        clock: Clock,
    ): CollectionTaskUseCase = CollectionTaskService(repository, sources, clock)

    @Bean
    fun collectionSlotService(
        tasks: CollectionTaskRepository,
        sources: SourceRepository,
        runs: CollectionRunRepository,
    ): CollectionSlotService = CollectionSlotService(tasks, sources, runs)

    @Bean
    fun collectionSchedulingUseCase(
        slots: CollectionSlotService,
        clock: Clock,
    ): CollectionSchedulingUseCase = CollectionSchedulingService(slots, clock)
}
