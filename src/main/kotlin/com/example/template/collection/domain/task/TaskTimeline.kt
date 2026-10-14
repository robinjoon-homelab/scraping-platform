package com.example.template.collection.domain.task

import com.example.template.collection.domain.validateCollectionInstant
import java.time.Instant

/** The creation time and the next slot the scheduler may claim, which is absent while the task is disabled. */
data class TaskTimeline(
    val createdAt: Instant,
    val nextRunAt: Instant? = null,
) {
    init {
        validateCollectionInstant(createdAt, "createdAt")
        if (nextRunAt != null) {
            validateCollectionInstant(nextRunAt, "nextRunAt")
        }
    }
}
