package com.example.template.config

import com.example.template.adapter.outbound.persistence.ExposedTodoRepository
import com.example.template.application.port.output.TodoRepository
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
class PersistenceConfig {
    @Bean
    fun database(dataSource: DataSource): Database = Database.connect(dataSource)

    @Bean
    fun todoRepository(database: Database): TodoRepository = ExposedTodoRepository(database)
}
