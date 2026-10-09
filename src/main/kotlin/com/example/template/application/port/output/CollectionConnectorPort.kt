package com.example.template.application.port.output

import com.example.template.domain.collection.CollectionBatch
import com.example.template.domain.collection.CollectionRequest

interface CollectionConnectorPort {
    /** Returns one bounded batch; results and its candidate checkpoint are not persisted by this contract. */
    fun collect(request: CollectionRequest): CollectionBatch
}
