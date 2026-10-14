package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import java.net.URI
import java.net.URISyntaxException

data class CreateSourceRequest(
    val name: String,
    val kind: SourceKind,
    val baseUrl: String,
    val credentialRef: String? = null,
) {
    fun toDefinition(): SourceDefinition {
        validateBaseUrl()
        return SourceDefinition(name, kind, baseUrl, credentialRef)
    }

    private fun validateBaseUrl() {
        val uri = parseBaseUrl()
        val validScheme = uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
        val validPort = uri.port == -1 || uri.port in 1..65535
        if (!validScheme || uri.host.isNullOrBlank() || !validPort) {
            throw InvalidCollectionConfigurationException("baseUrl must be an absolute HTTP(S) URL with a valid host and port")
        }
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
            throw InvalidCollectionConfigurationException("baseUrl must not contain credentials, a query, or a fragment")
        }
    }

    private fun parseBaseUrl(): URI =
        try {
            URI(baseUrl)
        } catch (exception: URISyntaxException) {
            throw InvalidCollectionConfigurationException("baseUrl must be a valid URI", exception)
        }
}
