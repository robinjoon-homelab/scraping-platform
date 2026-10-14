package com.example.template.collection.domain.task

import com.example.template.collection.domain.validateCollectionName
import java.util.UUID

data class TaskDefinition(
    val name: String,
    val sourceId: UUID,
    val target: CollectionTarget,
    val schedule: IntervalSchedule,
) {
    init {
        validateCollectionName(name)
    }
}
