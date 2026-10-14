package com.example.template.collection.domain.run

import com.example.template.collection.domain.validateCollectionConfiguration

// Status with its failure reason, so a stored row cannot name a reason for a run that did not fail.
data class RunState(
    val status: CollectionRunStatus,
    val failureReason: String? = null,
) {
    init {
        validateCollectionConfiguration(
            (status == CollectionRunStatus.FAILED) == (failureReason != null),
            "only a failed run has a failure reason",
        )
        if (failureReason != null) validateFailureReason(failureReason)
    }
}

internal const val MAX_FAILURE_LENGTH = 1000

internal fun validateFailureReason(reason: String) =
    validateCollectionConfiguration(
        reason.isNotBlank() && reason.length <= MAX_FAILURE_LENGTH,
        "failure reason must be 1..1000 characters",
    )
