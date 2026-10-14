package com.example.template.config

import com.example.template.collection.application.port.output.CollectionConnectorException
import com.example.template.collection.domain.run.CollectionRequest
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ConnectorConfigTest {
    private val at = Instant.parse("2026-10-05T00:00:00Z")
    private val connector = ConnectorConfig().collectionConnectorPort(HttpConnectorProperties(), Clock.fixed(at, ZoneOffset.UTC))

    @Test
    fun `the production HTTP_JSON connector refuses loopback and private destinations`() {
        listOf("http://127.0.0.1:1", "http://localhost:1", "http://10.43.0.1", "http://192.168.0.1").forEach { base ->
            assertThatThrownBy { connector.collect(request(SourceKind.HTTP_JSON, base)) }
                .isInstanceOf(CollectionConnectorException::class.java)
                .hasMessage("destination address is not allowed")
        }
    }

    @ParameterizedTest
    @EnumSource(SourceKind::class, names = ["RSS_ATOM", "HTML"])
    fun `kinds other than HTTP_JSON stay unsupported`(kind: SourceKind) {
        assertThatThrownBy { connector.collect(request(kind, "http://127.0.0.1:1")) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage("unsupported source kind: $kind")
    }

    private fun request(
        kind: SourceKind,
        base: String,
    ): CollectionRequest {
        val sourceId = UUID.randomUUID()
        val snapshot =
            TaskSnapshot(
                taskId = UUID.randomUUID(),
                version = 1,
                definition = TaskDefinition("Prices", sourceId, CollectionTarget("/data"), IntervalSchedule(3600, at)),
                source = SourceSnapshot(sourceId, SourceDefinition("Economy", kind, base, null), at),
            )
        return CollectionRequest(CollectionRun.pending(UUID.randomUUID(), snapshot, at).start(at))
    }
}
