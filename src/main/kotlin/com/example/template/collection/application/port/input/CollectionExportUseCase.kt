package com.example.template.collection.application.port.input

import com.example.template.collection.application.model.ExportPage
import com.example.template.collection.application.model.ExportStatus
import java.util.UUID

interface CollectionExportUseCase {
    fun configure(
        taskId: UUID,
        enabled: Boolean,
    ): ExportStatus

    fun status(taskId: UUID): ExportStatus

    fun pull(
        taskId: UUID,
        after: Long,
        limit: Int,
    ): ExportPage
}
