package com.example.template.config

import com.example.template.ordering.adapter.outbound.persistence.ExposedOfferRepository
import com.example.template.ordering.adapter.outbound.persistence.ExposedOrderRepository
import com.example.template.ordering.application.port.input.OfferUseCase
import com.example.template.ordering.application.port.input.OrderUseCase
import com.example.template.ordering.application.port.output.OfferRepository
import com.example.template.ordering.application.port.output.OrderRepository
import com.example.template.ordering.application.service.OfferService
import com.example.template.ordering.application.service.OrderService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class OrderingConfig {
    @Bean
    fun orderRepository(): OrderRepository = ExposedOrderRepository()

    @Bean
    fun offerRepository(): OfferRepository = ExposedOfferRepository()

    @Bean
    fun orderUseCase(
        repository: OrderRepository,
        offers: OfferRepository,
        clock: Clock,
    ): OrderUseCase = OrderService(repository, offers, clock)

    @Bean
    fun offerUseCase(repository: OfferRepository): OfferUseCase = OfferService(repository)
}
