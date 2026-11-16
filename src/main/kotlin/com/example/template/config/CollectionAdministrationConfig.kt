package com.example.template.config

import com.example.template.collection.adapter.outbound.persistence.ExposedCollectionControlStore
import com.example.template.collection.adapter.outbound.persistence.ExposedCollectionExportStore
import com.example.template.collection.application.port.input.CollectionExportUseCase
import com.example.template.collection.application.port.input.CollectionOperationsUseCase
import com.example.template.collection.application.port.output.CollectedRecordStore
import com.example.template.collection.application.port.output.CollectionConnectorPort
import com.example.template.collection.application.port.output.CollectionControlStore
import com.example.template.collection.application.port.output.CollectionExportStore
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.application.port.output.SourceRepository
import com.example.template.collection.application.service.CollectionExportService
import com.example.template.collection.application.service.CollectionOperationStateService
import com.example.template.collection.application.service.CollectionOperationsService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class CollectionAdministrationConfig(
    private val clock: Clock,
) {
    @Bean
    fun collectionControlStore(): CollectionControlStore = ExposedCollectionControlStore()

    @Bean
    fun collectionExportStore(): CollectionExportStore = ExposedCollectionExportStore()

    @Bean
    fun collectionOperationStateService(
        tasks: CollectionTaskRepository,
        sources: SourceRepository,
        runs: CollectionRunRepository,
        control: CollectionControlStore,
    ): CollectionOperationStateService = CollectionOperationStateService(tasks, sources, runs, control, clock)

    @Bean
    fun collectionOperationsUseCase(
        state: CollectionOperationStateService,
        connector: CollectionConnectorPort,
        clock: Clock,
    ): CollectionOperationsUseCase = CollectionOperationsService(state, connector, clock)

    @Bean
    fun collectionExportUseCase(
        tasks: CollectionTaskRepository,
        exports: CollectionExportStore,
        records: CollectedRecordStore,
        runs: CollectionRunRepository,
    ): CollectionExportUseCase = CollectionExportService(tasks, exports, records, runs)
}
