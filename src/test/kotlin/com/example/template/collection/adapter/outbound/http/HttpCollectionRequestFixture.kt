package com.example.template.collection.adapter.outbound.http

import com.example.template.collection.domain.run.CollectionRequest
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal fun httpCollectionRequest(base: String = "https://public.invalid"): CollectionRequest {
    val instant = Instant.parse("2026-10-05T00:00:00Z")
    val sourceId = UUID.randomUUID()
    val definition = TaskDefinition("Proxy", sourceId, CollectionTarget("/data", emptyMap()), IntervalSchedule(3600, instant))
    val source = SourceSnapshot(sourceId, SourceDefinition("Test", SourceKind.HTTP_JSON, base, null), instant)
    val snapshot = TaskSnapshot(UUID.randomUUID(), 1, definition, source)
    return CollectionRequest(CollectionRun.pending(UUID.randomUUID(), snapshot, instant).start(instant))
}

// A fresh JVM proves that process options supply the proxy and JSSE trust store without test SSLContext injection.
internal object JvmProxyCollectionProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val limits = HttpLimits(Duration.ofSeconds(3), Duration.ofSeconds(3), 1_000)
        val connector =
            HttpJsonCollectionConnector(limits, Clock.systemUTC(), PublicAddressPolicy) { host ->
                listOf(InetAddress.getByName(if (host == "proxy.invalid") "127.0.0.1" else "8.8.8.8"))
            }
        check(
            connector
                .collect(httpCollectionRequest(args.single()))
                .records
                .single()
                .payload == "{}",
        )
    }
}
