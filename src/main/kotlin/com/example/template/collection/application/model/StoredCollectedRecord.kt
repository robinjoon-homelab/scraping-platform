package com.example.template.collection.application.model

import com.example.template.collection.domain.run.CollectedRecord
import java.util.UUID

// A collected record with the id the store gave it; the record itself has no identity of its own.
data class StoredCollectedRecord(
    val id: UUID,
    val record: CollectedRecord,
)
