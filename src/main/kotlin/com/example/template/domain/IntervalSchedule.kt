package com.example.template.domain

import java.time.Duration
import java.time.Instant

data class IntervalSchedule(
    val everySeconds: Long,
    val anchorAt: Instant,
) {
    init {
        validateCollectionConfiguration(everySeconds in 60..31_536_000, "everySeconds must be between 60 and 31536000")
        validateCollectionInstant(anchorAt, "anchorAt")
    }

    fun nextAfter(reference: Instant): Instant {
        if (reference < anchorAt) {
            return anchorAt
        }
        validateCollectionInstant(reference, "reference")
        val elapsedSlots = Duration.between(anchorAt, reference).seconds / everySeconds
        val next = anchorAt.plusSeconds((elapsedSlots + 1) * everySeconds)
        validateCollectionInstant(next, "next scheduled time")
        return next
    }
}
