package com.example.template.collection.domain.run

import com.example.template.collection.domain.validateCollectionConfiguration
import com.example.template.collection.domain.validateCollectionInstant
import java.time.Instant

internal data class RunTimeline(
    val scheduledFor: Instant,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
) {
    init {
        validateCollectionInstant(scheduledFor, "collection timestamp")
        if (startedAt != null) {
            validateCollectionInstant(startedAt, "collection timestamp")
            validateCollectionConfiguration(startedAt >= scheduledFor, "run must not start before its scheduled time")
        }
        if (finishedAt != null) {
            validateCollectionInstant(finishedAt, "collection timestamp")
            validateCollectionConfiguration(startedAt != null && finishedAt >= startedAt, "run must not finish before it starts")
        }
    }
}
