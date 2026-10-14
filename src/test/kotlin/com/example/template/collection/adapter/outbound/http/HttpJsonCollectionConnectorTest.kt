package com.example.template.collection.adapter.outbound.http

import com.example.template.collection.application.port.output.CollectionConnectorException
import com.example.template.collection.domain.run.CollectionRequest
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.RecordProvenance
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

class HttpJsonCollectionConnectorTest {
    private val startedAt = Instant.parse("2026-10-05T00:00:00Z")
    private val collectedAt = startedAt.plusSeconds(1)
    private val clock = Clock.fixed(collectedAt, ZoneOffset.UTC)
    private val limits = HttpLimits(Duration.ofSeconds(2), Duration.ofSeconds(2), 1_000)
    private val anyAddress = DestinationAddressPolicy { true }
    private val release = CountDownLatch(1)
    private val receivedQueries = ConcurrentHashMap<String, String>()
    private val server =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            executor = Executors.newCachedThreadPool()
            start()
        }
    private val baseUrl = "http://127.0.0.1:${server.address.port}"

    @AfterEach
    fun stopServer() {
        release.countDown()
        server.stop(0)
    }

    @Test
    fun `a JSON response becomes one record keyed by the sorted, encoded request path`() {
        val body = """{"value":"가격","rows":[1,2]}"""
        respond("/api/v1/observations", 200, body, mapOf("Last-Modified" to "Wed, 21 Oct 2015 07:28:00 GMT"))
        val request = request("$baseUrl/api/", "/v1/observations", mapOf("series" to "a b&c", "area" to "서울", "id" to "+1"))

        val batch = HttpJsonCollectionConnector(limits, clock, anyAddress).collect(request)

        assertThat(batch.request).isSameAs(request)
        assertThat(batch.nextCheckpoint).isNull()
        val expectedQuery = "area=%EC%84%9C%EC%9A%B8&id=%2B1&series=a%20b%26c"
        assertThat(receivedQueries["/api/v1/observations"]).isEqualTo(expectedQuery)
        val record = batch.records.single()
        assertThat(record.identity.sourceRecordKey).isEqualTo("/api/v1/observations?$expectedQuery")
        assertThat(record.identity.revisionHash).isEqualTo(sha256(body.toByteArray()))
        assertThat(record.schemaVersion).isEqualTo("http-json/1")
        assertThat(record.payload).isEqualTo(body)
        assertThat(record.provenance).isEqualTo(
            RecordProvenance(request.run.id, request.run.task.source.id, collectedAt, Instant.parse("2015-10-21T07:28:00Z")),
        )
    }

    @Test
    fun `a target without parameters is keyed by its path, and an unparsable Last-Modified is left unknown`() {
        respond("/latest", 200, "[]", mapOf("Last-Modified" to "yesterday"))

        val record = HttpJsonCollectionConnector(limits, clock, anyAddress).collect(request(baseUrl, "/latest")).records.single()

        assertThat(record.identity.sourceRecordKey).isEqualTo("/latest")
        assertThat(record.provenance.sourceUpdatedAt).isNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 404, 429, 500, 503])
    fun `a non-success status fails with only the status code`(status: Int) {
        respond("/data", status, """{"error":"secret detail"}""")

        assertFailure(request(baseUrl, "/data"), "unexpected HTTP status $status")
    }

    @Test
    fun `a redirect is not followed`() {
        respond("/moved", 302, "", mapOf("Location" to "$baseUrl/target"))
        respond("/target", 200, "{}")

        assertFailure(request(baseUrl, "/moved"), "redirect not followed: HTTP status 302")
    }

    @Test
    fun `a body larger than the limit fails without reading the rest`() {
        respond("/large", 200, """{"data":"${"x".repeat(2_000)}"}""")

        assertFailure(request(baseUrl, "/large"), "response body exceeds 1000 bytes")
    }

    @Test
    fun `a response whose body is not complete within the response timeout fails`() {
        server.createContext("/slow") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.write("""{"partial":""".toByteArray())
            exchange.responseBody.flush()
            release.await(10, TimeUnit.SECONDS)
            exchange.close()
        }
        val connector = HttpJsonCollectionConnector(limits.copy(responseTimeout = Duration.ofMillis(300)), clock, anyAddress)

        assertThatThrownBy { connector.collect(request(baseUrl, "/slow")) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage("response timed out")
    }

    @ParameterizedTest
    @ValueSource(strings = ["<html>error</html>", "", "{\"a\":1} trailing", "{\"a\":"])
    fun `a body that is not exactly one JSON value fails`(body: String) {
        respond("/html", 200, body)

        assertFailure(request(baseUrl, "/html"), "response body is not valid JSON")
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://127.0.0.1:1", "http://localhost:1", "http://[::1]:1", "http://169.254.169.254"])
    fun `a host that resolves to a blocked address is refused before any request`(base: String) {
        val connector = HttpJsonCollectionConnector(limits, clock, PublicAddressPolicy)

        assertThatThrownBy { connector.collect(request(base, "/data")) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage("destination address is not allowed")
    }

    @Test
    fun `a host that resolves to a blocked address once the response arrives is discarded`() {
        respond("/data", 200, "{}")
        val answers = ArrayDeque(listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getLoopbackAddress()))
        val rebinding = HostResolver { listOf(answers.removeFirst()) }
        val connector = HttpJsonCollectionConnector(limits, clock, PublicAddressPolicy, rebinding)

        assertThatThrownBy { connector.collect(request(baseUrl, "/data")) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage("destination address is not allowed")
    }

    @Test
    fun `a host lookup slower than the collection deadline fails within the deadline`() {
        val slow =
            HostResolver {
                await()
                listOf(InetAddress.getByName("8.8.8.8"))
            }
        val connector = HttpJsonCollectionConnector(limits.copy(responseTimeout = Duration.ofMillis(200)), clock, anyAddress, slow)

        val elapsed = measure { assertFailure(connector, request(baseUrl, "/data"), "destination host resolution timed out") }

        assertThat(elapsed).isLessThan(Duration.ofSeconds(2))
    }

    @Test
    fun `the check after the response only gets the time left before the collection deadline`() {
        server.createContext("/slow-headers") { exchange ->
            Thread.sleep(150)
            reply(exchange, 200, "{}".toByteArray(), emptyMap())
        }
        val firstAnswered = AtomicBoolean()
        val slowSecondLookup =
            HostResolver {
                if (firstAnswered.getAndSet(true)) await()
                listOf(InetAddress.getByName("8.8.8.8"))
            }
        val connector =
            HttpJsonCollectionConnector(limits.copy(responseTimeout = Duration.ofMillis(400)), clock, anyAddress, slowSecondLookup)

        val elapsed = measure { assertFailure(connector, request(baseUrl, "/slow-headers"), "destination host resolution timed out") }

        assertThat(elapsed).isLessThan(Duration.ofSeconds(2))
    }

    @Test
    fun `a JVM error during the address check is not turned into a collection failure`() {
        val error = OutOfMemoryError("simulated")
        val connector = HttpJsonCollectionConnector(limits, clock, anyAddress) { throw error }

        assertThatThrownBy { connector.collect(request(baseUrl, "/data")) }.isSameAs(error)
    }

    @Test
    fun `a base URL with user information is refused`() {
        assertFailure(request("http://user:password@127.0.0.1:1", "/data"), "source base URL is not usable")
    }

    private fun assertFailure(
        request: CollectionRequest,
        message: String,
    ) = assertFailure(HttpJsonCollectionConnector(limits, clock, anyAddress), request, message)

    private fun assertFailure(
        connector: HttpJsonCollectionConnector,
        request: CollectionRequest,
        message: String,
    ) {
        assertThatThrownBy { connector.collect(request) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage(message)
    }

    // Blocks until the test ends, ignoring interruption like a lookup stuck in the operating system resolver.
    private fun await() {
        while (release.count > 0) LockSupport.parkNanos(Duration.ofMillis(10).toNanos())
    }

    private fun measure(action: () -> Unit): Duration {
        val start = System.nanoTime()
        action()
        return Duration.ofNanos(System.nanoTime() - start)
    }

    private fun respond(
        path: String,
        status: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ) {
        server.createContext(path) { exchange -> reply(exchange, status, body.toByteArray(), headers) }
    }

    private fun reply(
        exchange: HttpExchange,
        status: Int,
        body: ByteArray,
        headers: Map<String, String>,
    ) {
        receivedQueries[exchange.requestURI.rawPath] = exchange.requestURI.rawQuery.orEmpty()
        headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
        exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun sha256(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun request(
        base: String,
        resource: String,
        parameters: Map<String, String> = emptyMap(),
    ): CollectionRequest {
        val sourceId = UUID.randomUUID()
        val definition = TaskDefinition("Prices", sourceId, CollectionTarget(resource, parameters), IntervalSchedule(3600, startedAt))
        val snapshot =
            TaskSnapshot(
                taskId = UUID.randomUUID(),
                version = 1,
                definition = definition,
                source = SourceSnapshot(sourceId, SourceDefinition("Economy", SourceKind.HTTP_JSON, base, null), startedAt),
            )
        return CollectionRequest(CollectionRun.pending(UUID.randomUUID(), snapshot, startedAt).start(startedAt))
    }
}
