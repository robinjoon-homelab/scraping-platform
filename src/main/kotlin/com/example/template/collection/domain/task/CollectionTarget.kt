package com.example.template.collection.domain.task

import com.example.template.collection.domain.validateCollectionConfiguration
import java.util.Collections

class CollectionTarget(
    val resource: String,
    parameters: Map<String, String> = emptyMap(),
) {
    val parameters: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(parameters))

    init {
        validateCollectionConfiguration(
            resource.length <= 2048 && resource.startsWith('/') && !resource.startsWith("//"),
            "resource must be a relative path starting with a single slash, at most 2048 characters",
        )
        validateCollectionConfiguration(
            resource.none { it.isWhitespace() || it in "?#\\" } && resource.split('/').none { it == ".." },
            "resource must not contain whitespace, query, fragment, backslash or parent path segments",
        )
        validateCollectionConfiguration(this.parameters.size <= 50, "parameters must contain at most 50 entries")
        this.parameters.forEach { (key, value) ->
            validateCollectionConfiguration(key.isNotBlank() && key.length <= 100, "parameter keys must contain 1 through 100 characters")
            validateCollectionConfiguration(value.length <= 2000, "parameter values must contain at most 2000 characters")
        }
    }

    override fun equals(other: Any?): Boolean = other is CollectionTarget && resource == other.resource && parameters == other.parameters

    override fun hashCode(): Int = 31 * resource.hashCode() + parameters.hashCode()
}
