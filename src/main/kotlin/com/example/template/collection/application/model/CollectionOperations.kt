package com.example.template.collection.application.model

import com.example.template.collection.domain.run.CollectedRecord
import com.example.template.collection.domain.source.SourceKind

data class CollectorCapability(
    val kind: SourceKind,
    val supported: Boolean,
    val credentialSupported: Boolean,
    val recordUnit: String,
)

data class CollectionValidationResult(
    val executable: Boolean,
    val issues: List<String>,
)

data class CollectionPreview(
    val records: List<CollectedRecord>,
    val recordUnit: String,
)
