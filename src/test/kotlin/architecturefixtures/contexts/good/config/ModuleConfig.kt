package architecturefixtures.contexts.good.config

import architecturefixtures.contexts.good.adapter.outbound.sequence.SequenceIdGenerator
import architecturefixtures.contexts.good.application.port.output.IdGenerator
import architecturefixtures.contexts.good.inventory.adapter.outbound.memory.MemoryStockStore
import architecturefixtures.contexts.good.inventory.application.port.input.StockUseCase
import architecturefixtures.contexts.good.inventory.application.service.StockService
import architecturefixtures.contexts.good.ordering.adapter.outbound.memory.MemoryCartRepository
import architecturefixtures.contexts.good.ordering.adapter.outbound.memory.MemoryOrderRepository
import architecturefixtures.contexts.good.ordering.application.port.input.OrderUseCase
import architecturefixtures.contexts.good.ordering.application.service.OrderService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Root composition may see every context. It wires each context to its own adapters and the shared id port.
@Configuration(proxyBeanMethods = false)
class ModuleConfig {
    @Bean
    fun ids(): IdGenerator = SequenceIdGenerator()

    @Bean
    fun orders(ids: IdGenerator): OrderUseCase = OrderService(MemoryOrderRepository(), MemoryCartRepository(), ids)

    @Bean
    fun stock(): StockUseCase = StockService(MemoryStockStore())
}
