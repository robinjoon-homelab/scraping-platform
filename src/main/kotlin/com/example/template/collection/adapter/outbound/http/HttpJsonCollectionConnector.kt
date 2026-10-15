package com.example.template.collection.adapter.outbound.http

import com.example.template.collection.application.port.output.CollectionConnectorException
import com.example.template.collection.application.port.output.CollectionConnectorPort
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.run.CollectedRecord
import com.example.template.collection.domain.run.CollectionBatch
import com.example.template.collection.domain.run.CollectionRequest
import com.example.template.collection.domain.run.RecordIdentity
import com.example.template.collection.domain.run.RecordProvenance
import com.example.template.collection.domain.task.CollectionTarget
import org.apache.hc.client5.http.ConnectTimeoutException
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager
import org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy
import org.apache.hc.client5.http.ssl.TlsSocketStrategy
import org.apache.hc.core5.http.HttpHeaders
import org.apache.hc.core5.http.URIScheme
import org.apache.hc.core5.http.config.Lookup
import org.apache.hc.core5.http.config.RegistryBuilder
import org.apache.hc.core5.util.Timeout
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.HexFormat
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

// Sends one GET for the task's target and returns the whole JSON response as a single record without a checkpoint.
// limits.responseTimeout is one deadline for the whole collection: the address check, connecting, the request and the
// body all share it, so a slow DNS server cannot hold the worker past it. When the deadline passes the request is
// aborted, which closes its connection, rather than only abandoned.
//
// Every failure is a CollectionConnectorException with a fixed reason, because that message is stored as the run's
// failure reason. Reasons never name the host, the resolved address, the query, a header value or the body: the host and
// query are already in the source and task an operator can read, and echoing resolved addresses would let anyone who can
// define a source map the internal network through failure reasons.
//
// SSRF: the host is resolved once, and every address it resolves to must pass the address policy. HttpClient then gets a
// PinnedAddressResolver holding only the first of those addresses, in the order the resolver returned them, so it
// connects only to a checked address and never asks DNS again; a rebinding DNS server cannot point the connection
// elsewhere after the check. The original host name is kept for everything else: the Host header comes from the request
// URI, and the default TLS strategy sends it as SNI and verifies the certificate against it. No proxy is used, not even
// one configured by system properties, and redirects are not followed, so no other destination is ever contacted.
//
// There is no failover to the other addresses. HttpClient tries the next address after any connect or TLS failure,
// including the one an abort causes by closing the socket, and cancelling the request waits for the connection manager's
// lock while that next attempt runs. With several addresses an abort would therefore take up to another connectTimeout
// per address. The JDK HttpClient used before also connected to the first address only.
//
// An abort (the deadline, interruption, a body over the limit or a non-2xx status) first closes the exchange's own
// sockets through ExchangeSockets, and only then cancels the request. A closed socket makes the connecting thread fail
// and release the manager's lock at once, so the abort does not wait out a connect or TLS timeout, and a socket created
// after the abort is closed before it can connect.
//
// DefaultHttpClientConnectionOperator and DetachedSocketFactory are marked @Internal in HttpClient 5 (checked against
// 5.6.4 with core 5.4.3). When HttpClient is upgraded, check again that the connection goes only to the pinned address,
// that no proxy is used, that TLS still verifies the original host name, and that an abort still closes the connection
// and ends the exchange within the deadline.
class HttpJsonCollectionConnector(
    private val limits: HttpLimits,
    private val clock: Clock,
    private val addressPolicy: DestinationAddressPolicy,
    resolver: HostResolver = HostResolver.system,
) : CollectionConnectorPort {
    private val resolution = BoundedHostResolver(resolver, MAX_RESOLVER_THREADS)

    // The exchange runs on its own thread so that the caller can stop at the deadline, or on interruption, and abort it.
    // An aborted exchange ends as soon as its connection is closed, so these threads do not pile up.
    private val exchanges: ExecutorService =
        Executors.newCachedThreadPool(
            Thread
                .ofPlatform()
                .daemon()
                .name("http-connector-exchange-", 0)
                .factory(),
        )

    override fun collect(request: CollectionRequest): CollectionBatch {
        val task = request.run.task
        val uri = requestUri(task.source.definition.baseUrl, task.definition.target)
        val key = recordKey(uri)
        val host = checkNotNull(uri.host)
        val deadline = System.nanoTime() + limits.responseTimeout.toNanos()
        val address = permittedAddresses(host, deadline).first()
        val response = fetch(uri, PinnedAddressResolver(host, address), deadline)
        return toBatch(request, key, response)
    }

    private fun permittedAddresses(
        host: String,
        deadline: Long,
    ): List<InetAddress> {
        val addresses = resolution.resolve(host, remaining(deadline))
        if (addresses.isEmpty() || !addresses.all(addressPolicy::permits)) {
            throw CollectionConnectorException("destination address is not allowed")
        }
        return addresses
    }

    private fun fetch(
        uri: URI,
        pinned: PinnedAddressResolver,
        deadline: Long,
    ): FetchedResponse {
        val timeout = remaining(deadline)
        if (!timeout.isPositive) throw CollectionConnectorException("response timed out")
        val exchange = Exchange(HttpGet(uri).apply { setHeader(HttpHeaders.ACCEPT, "application/json") }, ExchangeSockets())
        val pending = exchanges.submit<FetchedResponse> { exchange(exchange, pinned, timeout) }
        try {
            return pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS)
        } catch (exception: TimeoutException) {
            exchange.abort()
            pending.cancel(true)
            throw CollectionConnectorException("response timed out", exception)
        } catch (exception: InterruptedException) {
            exchange.abort()
            pending.cancel(true)
            throw exception
        } catch (exception: ExecutionException) {
            throw describe(exception)
        }
    }

    private fun exchange(
        exchange: Exchange,
        pinned: PinnedAddressResolver,
        timeout: Duration,
    ): FetchedResponse =
        pinnedClient(pinned, exchange.sockets, timeout).use { client ->
            client.executeOpen(null, exchange.get, null).use { response ->
                val body = BoundedResponseBody.read(response, limits.maxBodyBytes, exchange::abort)
                FetchedResponse(body, response.getFirstHeader(HttpHeaders.LAST_MODIFIED)?.value)
            }
        }

    // A client per collection keeps the pinned address to this one request: its single connection is closed with it,
    // so nothing is reused for another host or another collection. Cookies, authentication caching, connection state,
    // retries, redirects and content decoding are off, so no state carries over and the body limit counts the bytes the
    // server sent. The socket timeout is only a backstop for a read that outlives an abort.
    private fun pinnedClient(
        pinned: PinnedAddressResolver,
        sockets: ExchangeSockets,
        timeout: Duration,
    ): CloseableHttpClient {
        val manager =
            BasicHttpClientConnectionManager(DefaultHttpClientConnectionOperator(sockets, null, pinned, tlsStrategies), null)
        manager.connectionConfig =
            ConnectionConfig
                .custom()
                .setConnectTimeout(Timeout.of(limits.connectTimeout))
                .setSocketTimeout(Timeout.of(timeout))
                .build()
        return HttpClients
            .custom()
            .setConnectionManager(manager)
            .setRoutePlanner(DefaultRoutePlanner(null))
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableAuthCaching()
            .disableConnectionState()
            .disableContentCompression()
            .build()
    }

    // JVM errors anywhere in the cause chain and interruption are rethrown as the same instance, because they are not
    // failures of the source: the worker ends its call and the lease expiry closes the run. HttpClient may wrap the
    // connector's own reason, so the chain is searched for it before the transport failure is named.
    private fun describe(failure: ExecutionException): Throwable {
        val causes = generateSequence(failure.cause) { it.cause }.toList()
        causes.filterIsInstance<Error>().firstOrNull()?.let { return it }
        (causes.firstOrNull() as? InterruptedException)?.let { return it }
        causes.filterIsInstance<CollectionConnectorException>().firstOrNull()?.let { return it }
        val reason =
            when (causes.firstOrNull()) {
                is ConnectTimeoutException -> "connection timed out"
                is SocketTimeoutException -> "response timed out"
                null -> "HTTP request failed"
                else -> "HTTP request failed with ${causes.first().javaClass.name}"
            }
        return CollectionConnectorException(reason, failure)
    }

    private fun toBatch(
        request: CollectionRequest,
        key: String,
        response: FetchedResponse,
    ): CollectionBatch {
        val body = response.body
        val payload = jsonText(body)
        val provenance =
            RecordProvenance(request.run.id, request.run.task.source.id, Instant.now(clock), lastModified(response.lastModified))
        return try {
            val identity = RecordIdentity(key, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)))
            CollectionBatch(request, listOf(CollectedRecord(identity, SCHEMA_VERSION, payload, provenance)))
        } catch (exception: InvalidCollectionConfigurationException) {
            // Only reachable when maxBodyBytes is configured above the record payload limit of 1,000,000 characters.
            throw CollectionConnectorException("response does not fit a collected record", exception)
        }
    }

    // RFC 8259 requires UTF-8 between systems. The Content-Type header is not trusted because APIs often mislabel JSON,
    // so the body itself must be exactly one well-formed JSON value; an HTML error page served with 200 is rejected.
    private fun jsonText(body: ByteArray): String {
        val text =
            try {
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString()
            } catch (exception: CharacterCodingException) {
                throw CollectionConnectorException("response body is not valid UTF-8", exception)
            }
        val wellFormed =
            try {
                jsonMapper.createParser(text).use { parser ->
                    parser.nextToken() != null && parser.skipChildren().nextToken() == null
                }
            } catch (exception: JacksonException) {
                throw CollectionConnectorException("response body is not valid JSON", exception)
            }
        if (!wellFormed) throw CollectionConnectorException("response body is not valid JSON")
        return text
    }

    // An unparsable Last-Modified only means the source time is unknown; it never fails the run.
    private fun lastModified(value: String?): Instant? {
        if (value == null) return null
        return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
            .onFailure { if (it !is DateTimeException) throw it }
            .getOrNull()
    }

    private fun remaining(deadline: Long): Duration = Duration.ofNanos(deadline - System.nanoTime())

    // The sockets are closed before the request is cancelled; the class comment explains why the order matters.
    private class Exchange(
        val get: HttpGet,
        val sockets: ExchangeSockets,
    ) {
        fun abort() {
            sockets.cancel()
            get.cancel()
        }
    }

    private class FetchedResponse(
        val body: ByteArray,
        val lastModified: String?,
    )

    private companion object {
        const val SCHEMA_VERSION = "http-json/1"

        // Sources are collected one at a time, so a few threads cover lookups that outlive their deadline.
        const val MAX_RESOLVER_THREADS = 4

        // RecordIdentity accepts source record keys of at most this many characters.
        const val MAX_RECORD_KEY_LENGTH = 2048
        val supportedSchemes = setOf("http", "https")
        val jsonMapper: JsonMapper = JsonMapper.shared()

        // The default strategy uses the JVM trust store and verifies the certificate against the host name it is given.
        val tlsStrategies: Lookup<TlsSocketStrategy> =
            RegistryBuilder
                .create<TlsSocketStrategy>()
                .register(URIScheme.HTTPS.id, DefaultClientTlsStrategy.createDefault())
                .build()

        // The base URL is checked again here because stored sources predate any connector and the domain only checks
        // the URL's shape. Trailing slashes of the base path are dropped so the resource's leading slash joins it.
        fun requestUri(
            baseUrl: String,
            target: CollectionTarget,
        ): URI {
            val base = parseUri(baseUrl, "source base URL is not usable")
            val usable =
                base.scheme?.lowercase() in supportedSchemes && base.host != null &&
                    base.rawUserInfo == null && base.rawQuery == null && base.rawFragment == null
            if (!usable) throw CollectionConnectorException("source base URL is not usable")
            val query = queryString(target.parameters)
            val text = baseUrl.trimEnd('/') + target.resource + if (query.isEmpty()) "" else "?$query"
            return parseUri(text, "request URL is not valid").normalize()
        }

        // Keys are sorted so the same parameters always produce the same URL and record key. Spaces become %20 rather
        // than the form encoding's plus sign, which some APIs read literally.
        fun queryString(parameters: Map<String, String>): String =
            parameters.toSortedMap().entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

        fun parseUri(
            text: String,
            reason: String,
        ): URI =
            try {
                URI(text)
            } catch (exception: URISyntaxException) {
                throw CollectionConnectorException(reason, exception)
            }

        fun recordKey(uri: URI): String {
            val key = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
            if (key.length > MAX_RECORD_KEY_LENGTH) throw CollectionConnectorException("request URL is too long for a record key")
            return key
        }
    }
}
