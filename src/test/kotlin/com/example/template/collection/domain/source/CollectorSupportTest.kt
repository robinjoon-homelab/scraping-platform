package com.example.template.collection.domain.source

import com.example.template.collection.domain.InvalidCollectionConfigurationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CollectorSupportTest {
    @Test
    fun `only unauthenticated HTTP JSON is executable with actionable field errors`() {
        val supported = SourceDefinition("Public", SourceKind.HTTP_JSON, "https://example.com")
        assertThat(CollectorSupport.issues(supported)).isEmpty()
        val unsupported = supported.copy(kind = SourceKind.HTML, credentialRef = "secret-ref")
        assertThat(CollectorSupport.issues(unsupported)).hasSize(2)
        assertThatThrownBy { CollectorSupport.requireExecutable(unsupported) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
            .hasMessageContaining("source.kind")
            .hasMessageContaining("source.credentialRef")
    }
}
