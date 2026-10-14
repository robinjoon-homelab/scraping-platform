package com.example.template.collection.application.port.output

import com.example.template.collection.domain.run.CollectionBatch
import com.example.template.collection.domain.run.CollectionRequest

interface CollectionConnectorPort {
    /** Returns one bounded batch; results and its candidate checkpoint are not persisted by this contract. */
    fun collect(request: CollectionRequest): CollectionBatch
}
