package com.example.template.collection.domain.source

import com.example.template.collection.domain.validateCollectionConfiguration
import com.example.template.collection.domain.validateCollectionName

data class SourceDefinition(
    val name: String,
    val kind: SourceKind,
    val baseUrl: String,
    val credentialRef: String? = null,
) {
    init {
        validateCollectionName(name)
        validateCollectionConfiguration(
            baseUrl.length <= 2048 && baseUrl.matches(Regex("https?://\\S+", RegexOption.IGNORE_CASE)),
            "baseUrl must be an HTTP(S) URL of at most 2048 characters",
        )
        validateCollectionConfiguration(
            credentialRef == null || credentialRef.matches(Regex("[A-Za-z0-9._-]{1,120}")),
            "credentialRef must contain 1 through 120 letters, digits, dots, underscores or hyphens",
        )
    }
}
