package com.example.template.collection.application.model

import com.example.template.collection.domain.run.TaskSnapshot
import java.util.UUID

data class ExportStatus(
    val taskId: UUID,
    val enabled: Boolean,
    val publishedThrough: Long,
    val publishedCount: Long,
)

data class ExportItem(
    val sequence: Long,
    val record: StoredCollectedRecord,
    val task: TaskSnapshot,
)

data class ExportPage(
    val taskId: UUID,
    val items: List<ExportItem>,
    val nextAfter: Long,
)
