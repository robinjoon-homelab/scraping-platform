package com.example.template.config

import com.example.template.adapter.outbound.persistence.ExposedCollectionTaskRepository
import com.example.template.adapter.outbound.persistence.ExposedSourceRepository
import com.example.template.application.port.output.CollectionTaskRepository
import com.example.template.application.port.output.SourceRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
class PersistenceConfig {
    @Bean
    fun sourceRepository(): SourceRepository = ExposedSourceRepository()

    @Bean
    fun collectionTaskRepository(jsonMapper: JsonMapper): CollectionTaskRepository = ExposedCollectionTaskRepository(jsonMapper)
}
