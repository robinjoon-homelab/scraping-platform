package com.example.template.domain.collection

import com.example.template.domain.validateCollectionConfiguration

data class CollectionRequest(
    val run: CollectionRun,
    val checkpoint: Checkpoint? = null,
    val maxRecords: Int = 100,
) {
    init {
        validateCollectionConfiguration(run.status == CollectionRunStatus.RUNNING, "collection requires a running run")
        validateCollectionConfiguration(maxRecords in 1..MAX_RECORDS, "collection batch limit must be 1..1000 records")
        checkpoint?.requireMatches(run.task)
    }

    companion object {
        private const val MAX_RECORDS = 1000
    }
}
