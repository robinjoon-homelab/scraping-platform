package com.example.template.collection.application.port.output

import com.example.template.collection.domain.run.CollectionBatch
import com.example.template.collection.domain.run.CollectionRequest

interface CollectionConnectorPort {
    /**
     * Returns one bounded batch for the given request; results and its candidate checkpoint are not persisted by this
     * contract. Throw [CollectionConnectorException] with a reason free of response bodies and secrets to fail the run.
     */
    fun collect(request: CollectionRequest): CollectionBatch
}
