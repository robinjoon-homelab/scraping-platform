package com.example.template.collection.adapter.outbound.connector

import com.example.template.collection.application.port.output.CollectionConnectorException
import com.example.template.collection.application.port.output.CollectionConnectorPort
import com.example.template.collection.domain.run.CollectionBatch
import com.example.template.collection.domain.run.CollectionRequest
import com.example.template.collection.domain.source.SourceKind

// Delegates by the run's source kind. A kind without a connector fails the run with a fixed reason instead of a
// connector-specific one, so no source data reaches the failure text.
class RoutingCollectionConnector(
    private val connectors: Map<SourceKind, CollectionConnectorPort> = emptyMap(),
) : CollectionConnectorPort {
    override fun collect(request: CollectionRequest): CollectionBatch {
        val kind = request.run.task.source.definition.kind
        val connector = connectors[kind] ?: throw CollectionConnectorException("unsupported source kind: $kind")
        return connector.collect(request)
    }
}
