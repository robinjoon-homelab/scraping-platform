package com.example.template.config

import com.example.template.inventory.adapter.outbound.persistence.ExposedStockItemRepository
import com.example.template.inventory.application.port.input.StockUseCase
import com.example.template.inventory.application.port.output.StockItemRepository
import com.example.template.inventory.application.service.StockService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class InventoryConfig {
    @Bean
    fun stockItemRepository(): StockItemRepository = ExposedStockItemRepository()

    @Bean
    fun stockUseCase(repository: StockItemRepository): StockUseCase = StockService(repository)
}
