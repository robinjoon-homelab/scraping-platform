package com.example.template.config

import com.example.template.collection.adapter.inbound.scheduler.CollectionScheduler
import com.example.template.collection.application.port.input.CollectionSchedulingUseCase
import com.example.template.collection.application.port.input.CollectionWorkerUseCase
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

// Scheduling is enabled only here, so scraping.worker.enabled=false leaves no scheduler running and no background
// database access.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "scraping.worker", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class WorkerConfig {
    @Bean
    fun collectionScheduler(
        worker: CollectionWorkerUseCase,
        scheduling: CollectionSchedulingUseCase,
        properties: WorkerProperties,
    ): CollectionScheduler = CollectionScheduler(worker, scheduling, properties.batchSize)
}
