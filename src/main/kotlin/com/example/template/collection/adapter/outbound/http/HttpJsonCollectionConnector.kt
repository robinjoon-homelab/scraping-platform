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
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

// Sends one GET for the task's target and returns the whole JSON response as a single record without a checkpoint.
// limits.responseTimeout is one deadline for the whole collection: both address checks, connecting, the request and the
// body all share it, so a slow DNS server cannot hold the worker past it.
//
// Every failure is a CollectionConnectorException with a fixed reason, because that message is stored as the run's
// failure reason. Reasons never name the host, the resolved address, the query, a header value or the body: the host and
// query are already in the source and task an operator can read, and echoing resolved addresses would let anyone who can
// define a source map the internal network through failure reasons.
//
// SSRF: before the request every address the host resolves to must pass the address policy. HttpClient cannot be
// pinned to the checked address; it resolves the host again when it connects. That second lookup normally hits the
// JVM address cache filled by the check (networkaddress.cache.ttl, 30 seconds by default), but a cache entry that
// expires between the check and the connection lets a rebinding DNS server answer the second lookup with an internal
// address. The check is therefore repeated after the response arrives, while the address HttpClient used is still
// cached, and a response from a host that now resolves to a blocked address is discarded. This keeps internal responses
// out of the stored records but does not stop the request itself from reaching the internal address in that window, and
// with networkaddress.cache.ttl=0 both checks may see different answers from the connection. Network egress policy is
// the defense that closes this gap.
class HttpJsonCollectionConnector(
    private val limits: HttpLimits,
    private val clock: Clock,
    private val addressPolicy: DestinationAddressPolicy,
    resolver: HostResolver = HostResolver.system,
) : CollectionConnectorPort {
    private val resolution = BoundedHostResolver(resolver, MAX_RESOLVER_THREADS)

    // No proxy, so the checked host is the one HttpClient connects to.
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(limits.connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(HttpClient.Builder.NO_PROXY)
            .build()

    override fun collect(request: CollectionRequest): CollectionBatch {
        val task = request.run.task
        val uri = requestUri(task.source.definition.baseUrl, task.definition.target)
        val key = recordKey(uri)
        val host = checkNotNull(uri.host)
        val deadline = System.nanoTime() + limits.responseTimeout.toNanos()
        requirePermittedHost(host, deadline)
        val response = fetch(uri, deadline)
        requirePermittedHost(host, deadline)
        return toBatch(request, key, response)
    }

    private fun requirePermittedHost(
        host: String,
        deadline: Long,
    ) {
        val addresses = resolution.resolve(host, remaining(deadline))
        if (addresses.isEmpty() || !addresses.all(addressPolicy::permits)) {
            throw CollectionConnectorException("destination address is not allowed")
        }
    }

    private fun fetch(
        uri: URI,
        deadline: Long,
    ): HttpResponse<ByteArray> {
        val timeout = remaining(deadline)
        if (!timeout.isPositive) throw CollectionConnectorException("response timed out")
        val httpRequest =
            HttpRequest
                .newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .timeout(timeout)
                .build()
        val pending = client.sendAsync(httpRequest, BoundedBodySubscriber.handler(limits.maxBodyBytes))
        try {
            return pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS)
        } catch (exception: TimeoutException) {
            pending.cancel(true)
            throw CollectionConnectorException("response timed out", exception)
        } catch (exception: InterruptedException) {
            pending.cancel(true)
            throw exception
        } catch (exception: ExecutionException) {
            throw describe(exception)
        }
    }

    // HttpClient may wrap the subscriber's own reason, so the cause chain is searched for it first. JVM errors anywhere in
    // the chain and interruption are rethrown as the same instance, because they are not failures of the source: the
    // worker ends its call and the lease expiry closes the run.
    private fun describe(failure: ExecutionException): Throwable {
        val causes = generateSequence(failure.cause) { it.cause }.toList()
        causes.filterIsInstance<Error>().firstOrNull()?.let { return it }
        (causes.firstOrNull() as? InterruptedException)?.let { return it }
        causes.filterIsInstance<CollectionConnectorException>().firstOrNull()?.let { return it }
        val reason =
            when (causes.firstOrNull()) {
                is HttpConnectTimeoutException -> "connection timed out"
                is HttpTimeoutException -> "response timed out"
                null -> "HTTP request failed"
                else -> "HTTP request failed with ${causes.first().javaClass.name}"
            }
        return CollectionConnectorException(reason, failure)
    }

    private fun toBatch(
        request: CollectionRequest,
        key: String,
        response: HttpResponse<ByteArray>,
    ): CollectionBatch {
        val body = response.body()
        val payload = jsonText(body)
        val provenance =
            RecordProvenance(request.run.id, request.run.task.source.id, Instant.now(clock), lastModified(response))
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
    private fun lastModified(response: HttpResponse<ByteArray>): Instant? {
        val value = response.headers().firstValue("Last-Modified").orElse(null) ?: return null
        return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
            .onFailure { if (it !is DateTimeException) throw it }
            .getOrNull()
    }

    private fun remaining(deadline: Long): Duration = Duration.ofNanos(deadline - System.nanoTime())

    private companion object {
        const val SCHEMA_VERSION = "http-json/1"

        // Sources are collected one at a time, so a few threads cover lookups that outlive their deadline.
        const val MAX_RESOLVER_THREADS = 4

        // RecordIdentity accepts source record keys of at most this many characters.
        const val MAX_RECORD_KEY_LENGTH = 2048
        val supportedSchemes = setOf("http", "https")
        val jsonMapper: JsonMapper = JsonMapper.shared()

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
