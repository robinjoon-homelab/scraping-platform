package com.example.template.collection.adapter.outbound.connector

import com.example.template.collection.application.port.output.CollectionConnectorException
import com.example.template.collection.application.port.output.CollectionConnectorPort
import com.example.template.collection.domain.run.CollectionBatch
import com.example.template.collection.domain.run.CollectionRequest
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import java.util.UUID

class RoutingCollectionConnectorTest {
    private val at = Instant.parse("2026-10-05T00:00:00Z")

    @ParameterizedTest
    @EnumSource(SourceKind::class)
    fun `every source kind is unsupported while no connector is registered`(kind: SourceKind) {
        assertThatThrownBy { RoutingCollectionConnector().collect(request(kind)) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage("unsupported source kind: $kind")
    }

    @Test
    fun `a registered kind is delegated to its connector and other kinds stay unsupported`() {
        val request = request(SourceKind.HTTP_JSON)
        val batch = CollectionBatch(request, emptyList())
        val routing =
            RoutingCollectionConnector(
                mapOf(
                    SourceKind.HTTP_JSON to
                        object : CollectionConnectorPort {
                            override fun collect(request: CollectionRequest): CollectionBatch = batch
                        },
                ),
            )

        assertThat(routing.collect(request)).isSameAs(batch)
        assertThatThrownBy { routing.collect(request(SourceKind.HTML)) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage("unsupported source kind: HTML")
    }

    private fun request(kind: SourceKind): CollectionRequest {
        val sourceId = UUID.randomUUID()
        val snapshot =
            TaskSnapshot(
                taskId = UUID.randomUUID(),
                version = 1,
                definition = TaskDefinition("Consumer prices", sourceId, CollectionTarget("/observations"), IntervalSchedule(3600, at)),
                source = SourceSnapshot(sourceId, SourceDefinition("Economy", kind, "https://example.com", null), at),
            )
        return CollectionRequest(CollectionRun.pending(UUID.randomUUID(), snapshot, at).start(at))
    }
}
