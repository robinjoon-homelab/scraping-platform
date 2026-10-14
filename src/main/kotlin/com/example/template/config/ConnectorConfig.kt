package com.example.template.config

import com.example.template.collection.adapter.outbound.connector.RoutingCollectionConnector
import com.example.template.collection.application.port.output.CollectionConnectorPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ConnectorConfig {
    // No source kind has a connector yet, so every run fails with an unsupported-kind reason.
    @Bean
    fun collectionConnectorPort(): CollectionConnectorPort = RoutingCollectionConnector()
}
