package com.example.template.collection.application.port.input

import com.example.template.collection.application.model.CollectionPreview
import com.example.template.collection.application.model.CollectionValidationResult
import com.example.template.collection.application.model.CollectorCapability
import com.example.template.collection.domain.run.CollectionRun
import java.util.UUID

interface CollectionOperationsUseCase {
    fun capabilities(): List<CollectorCapability>

    fun validate(taskId: UUID): CollectionValidationResult

    fun preview(taskId: UUID): CollectionPreview

    fun runNow(
        taskId: UUID,
        expectedVersion: Long,
        requestKey: String,
    ): CollectionRun
}
