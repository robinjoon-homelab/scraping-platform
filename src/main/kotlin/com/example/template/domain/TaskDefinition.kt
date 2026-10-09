package com.example.template.domain

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
