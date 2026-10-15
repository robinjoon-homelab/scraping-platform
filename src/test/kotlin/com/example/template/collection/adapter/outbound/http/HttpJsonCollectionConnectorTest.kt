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
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

class HttpJsonCollectionConnectorTest {
    private val startedAt = Instant.parse("2026-10-05T00:00:00Z")
    private val collectedAt = startedAt.plusSeconds(1)
    private val clock = Clock.fixed(collectedAt, ZoneOffset.UTC)
    private val limits = HttpLimits(Duration.ofSeconds(2), Duration.ofSeconds(2), 1_000)
    private val anyAddress = DestinationAddressPolicy { true }
    private val loopback = InetAddress.getByName("127.0.0.1")
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

        val elapsed = measure { assertFailure(connector, request(baseUrl, "/slow"), "response timed out") }

        assertThat(elapsed).isLessThan(Duration.ofSeconds(2))
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
    fun `the connection goes to the address the check resolved, not to one the system resolver would find`() {
        respond("/data", 200, """{"pinned":true}""")
        val pinned = HostResolver { host -> if (host == "pinned.invalid") listOf(loopback) else emptyList() }
        val onlyLoopback = DestinationAddressPolicy { it == loopback }
        val connector = HttpJsonCollectionConnector(limits, clock, onlyLoopback, pinned)

        val record = connector.collect(request("http://pinned.invalid:${server.address.port}", "/data")).records.single()

        assertThat(record.payload).isEqualTo("""{"pinned":true}""")
    }

    @Test
    fun `an address the check resolved is refused when the policy blocks it`() {
        respond("/data", 200, "{}")
        val connector = HttpJsonCollectionConnector(limits, clock, PublicAddressPolicy) { listOf(loopback) }

        assertFailure(connector, request("http://pinned.invalid:${server.address.port}", "/data"), "destination address is not allowed")
    }

    // The server listens only on 127.0.0.1, which the URL names and the policy blocks. The check resolves the host to
    // another loopback address that the policy permits and nothing listens on, so the packets never leave the machine. The
    // connection must go to that checked address and fail, instead of reaching the server and storing its body.
    @Test
    fun `a host checked at one address is never connected at another`() {
        respond("/data", 200, """{"probe":"loopback-only"}""")
        val checked = InetAddress.getByName("127.0.0.2")
        val connector =
            HttpJsonCollectionConnector(limits.copy(connectTimeout = Duration.ofMillis(300)), clock, { it == checked }) { listOf(checked) }

        assertThatThrownBy { connector.collect(request(baseUrl, "/data")) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .message()
            .isIn("connection timed out", "HTTP request failed with org.apache.hc.client5.http.HttpHostConnectException")
    }

    // The resolver returns the stalled server twice because a second loopback address on the same port is not available on
    // every machine (127.0.0.2 is not configured on macOS, ::1 is missing where IPv6 is off). A connector that moved on to
    // the next address after the abort would wait out another connectTimeout on the second attempt.
    @Test
    fun `a TLS handshake stalled on the first of several addresses ends at the collection deadline`() {
        val stalled = ServerSocket(0, 0, loopback)
        val accepted = CopyOnWriteArrayList<Socket>()
        thread(isDaemon = true) { runCatching { while (!stalled.isClosed) accepted += stalled.accept() } }
        val connector =
            HttpJsonCollectionConnector(HttpLimits(Duration.ofSeconds(3), Duration.ofMillis(300), 1_000), clock, anyAddress) {
                listOf(loopback, loopback)
            }

        try {
            val elapsed =
                measure { assertFailure(connector, request("https://pinned.invalid:${stalled.localPort}", "/data"), "response timed out") }

            assertThat(elapsed).isLessThan(Duration.ofMillis(1_500))
            assertThat(lastRead(accepted.single())).isEqualTo(-1)
        } finally {
            stalled.close()
            accepted.forEach(Socket::close)
        }
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
    fun `the request only gets the time the address check left before the collection deadline`() {
        server.createContext("/slow-headers") { exchange ->
            Thread.sleep(200)
            reply(exchange, 200, "{}".toByteArray(), emptyMap())
        }
        val slowLookup =
            HostResolver {
                Thread.sleep(200)
                listOf(loopback)
            }
        val connector = HttpJsonCollectionConnector(limits.copy(responseTimeout = Duration.ofMillis(300)), clock, anyAddress, slowLookup)

        val elapsed = measure { assertFailure(connector, request(baseUrl, "/slow-headers"), "response timed out") }

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

    // Skips what the client sent, such as a TLS ClientHello, and returns -1 once the client has closed the connection.
    private fun lastRead(peer: Socket): Int {
        peer.soTimeout = 1_000
        val buffer = ByteArray(1_024)
        var read = peer.getInputStream().read(buffer)
        while (read > 0) read = peer.getInputStream().read(buffer)
        return read
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
