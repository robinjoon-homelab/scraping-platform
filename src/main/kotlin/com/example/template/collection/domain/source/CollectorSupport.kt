package com.example.template.collection.domain.source

import com.example.template.collection.domain.validateCollectionConfiguration

object CollectorSupport {
    fun issues(source: SourceDefinition): List<String> =
        buildList {
            if (source.kind != SourceKind.HTTP_JSON) add("source.kind: only HTTP_JSON is executable")
            if (source.credentialRef != null) add("source.credentialRef: credential authentication is unsupported")
        }

    fun requireExecutable(source: SourceDefinition) {
        val issues = issues(source)
        validateCollectionConfiguration(issues.isEmpty(), issues.joinToString("; "))
    }
}
