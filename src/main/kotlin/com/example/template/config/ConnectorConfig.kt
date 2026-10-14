package com.example.template.config

import com.example.template.collection.adapter.outbound.connector.RoutingCollectionConnector
import com.example.template.collection.adapter.outbound.http.HttpJsonCollectionConnector
import com.example.template.collection.adapter.outbound.http.HttpLimits
import com.example.template.collection.adapter.outbound.http.PublicAddressPolicy
import com.example.template.collection.application.port.output.CollectionConnectorPort
import com.example.template.collection.domain.source.SourceKind
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HttpConnectorProperties::class)
class ConnectorConfig {
    // Only HTTP_JSON has a connector; RSS_ATOM and HTML runs fail with an unsupported-kind reason. The HTTP connector is
    // built here rather than exposed as a bean so that CollectionConnectorPort has a single candidate, and it always uses
    // PublicAddressPolicy: no setting can let a source reach loopback, private or cluster-internal addresses.
    @Bean
    fun collectionConnectorPort(
        http: HttpConnectorProperties,
        clock: Clock,
    ): CollectionConnectorPort {
        val limits = HttpLimits(http.connectTimeout, http.responseTimeout, http.maxBodyBytes)
        return RoutingCollectionConnector(
            mapOf(SourceKind.HTTP_JSON to HttpJsonCollectionConnector(limits, clock, PublicAddressPolicy)),
        )
    }
}
