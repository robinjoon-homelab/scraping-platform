package com.example.template.collection.application.port.output

import com.example.template.collection.application.model.ExportStatus
import java.util.UUID

interface CollectionExportStore {
    fun configure(
        taskId: UUID,
        enabled: Boolean,
    )

    fun status(taskId: UUID): ExportStatus

    // Serialized publication discovers committed records, never an allocation-time high-water mark.
    fun publish(
        taskId: UUID,
        limit: Int,
    )

    fun page(
        taskId: UUID,
        after: Long,
        limit: Int,
    ): List<Pair<Long, UUID>>
}
