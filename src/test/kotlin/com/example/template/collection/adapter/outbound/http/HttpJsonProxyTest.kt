package com.example.template.collection.adapter.outbound.http

import com.example.template.collection.application.port.output.CollectionConnectorException
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import kotlin.concurrent.thread

@ResourceLock(Resources.SYSTEM_PROPERTIES)
@ResourceLock("javax.net.ssl.SSLContext.default")
internal class HttpJsonProxyTest {
    private val originalContext = SSLContext.getDefault()
    private val properties = listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort", "http.nonProxyHosts")
    private val originalProperties = properties.associateWith(System::getProperty)
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val publicAddress = InetAddress.getByName("8.8.8.8")
    private val limits = HttpLimits(Duration.ofSeconds(3), Duration.ofSeconds(3), 1_000)

    @BeforeEach
    fun configureDefaults() {
        properties.forEach(System::clearProperty)
        SSLContext.setDefault(certificates.clientContext)
    }

    @AfterEach
    fun restoreDefaults() {
        originalProperties.forEach { (name, value) -> if (value == null) System.clearProperty(name) else System.setProperty(name, value) }
        SSLContext.setDefault(originalContext)
    }

    @Test
    fun `HTTPS uses configured CONNECT with destination Host SNI and certificate identity`() {
        InspectionProxyFixture(certificates.serverContext, response = "{\"proxy\":true}").use { proxy ->
            configureProxy(proxy.port)

            val record = connector().collect(httpCollectionRequest()).records.single()

            assertThat(record.payload).isEqualTo("{\"proxy\":true}")
            val observed = proxy.request.get(1, TimeUnit.SECONDS)
            assertThat(observed.connect).isEqualTo("CONNECT public.invalid:443 HTTP/1.1")
            assertThat(observed.headers).contains("GET /data HTTP/1.1", "Host: public.invalid", "Accept: application/json")
            assertThat(observed.sni).containsExactly("public.invalid")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["none", "public.invalid", "*.invalid"])
    fun `no proxy or a nonProxyHosts match preserves pinned direct HTTPS`(excluded: String) {
        InspectionProxyFixture(certificates.serverContext, direct = true).use { destination ->
            if (excluded != "none") {
                configureProxy(1)
                System.setProperty("http.nonProxyHosts", excluded)
            }
            val direct = HttpJsonCollectionConnector(limits, Clock.systemUTC(), { it == loopback }) { listOf(loopback) }

            assertThat(
                direct
                    .collect(httpCollectionRequest("https://public.invalid:${destination.port}"))
                    .records
                    .single()
                    .payload,
            ).isEqualTo("{}")
            assertThat(destination.request.get(1, TimeUnit.SECONDS).connect).isNull()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["127.0.0.1", "10.42.0.1", "169.254.169.254", "::1"])
    fun `configured proxy never bypasses destination address rejection`(address: String) {
        configureProxy(1)
        val blocked = HttpJsonCollectionConnector(limits, Clock.systemUTC(), PublicAddressPolicy) { listOf(InetAddress.getByName(address)) }

        assertThatThrownBy { blocked.collect(httpCollectionRequest()) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage("destination address is not allowed")
    }

    @Test
    fun `a mixed public and private DNS result is rejected in proxy mode`() {
        configureProxy(1)
        val mixed = HttpJsonCollectionConnector(limits, Clock.systemUTC(), PublicAddressPolicy) { listOf(publicAddress, loopback) }

        assertThatThrownBy { mixed.collect(httpCollectionRequest()) }.hasMessage("destination address is not allowed")
    }

    @Test
    fun `an untrusted inspection certificate is rejected with a safe error`() {
        InspectionProxyFixture(certificates.untrustedContext).use { proxy ->
            configureProxy(proxy.port)

            assertThatThrownBy { connector().collect(httpCollectionRequest()) }
                .isInstanceOf(CollectionConnectorException::class.java)
                .hasMessage("HTTP request failed with javax.net.ssl.SSLHandshakeException")
        }
    }

    @Test
    fun `a trusted certificate for another destination is rejected`() {
        InspectionProxyFixture(certificates.serverContext).use { proxy ->
            configureProxy(proxy.port)

            assertThatThrownBy { connector().collect(httpCollectionRequest("https://other.invalid")) }
                .isInstanceOf(CollectionConnectorException::class.java)
                .hasMessage("HTTP request failed with javax.net.ssl.SSLHandshakeException")
        }
    }

    @ParameterizedTest
    @EnumSource(InspectionProxyFixture.Stage::class, names = ["CONNECT_STALL", "TLS_STALL", "HEADERS_STALL", "BODY_STALL"])
    fun `deadline closes owned proxy socket during CONNECT TLS headers and body`(stage: InspectionProxyFixture.Stage) {
        InspectionProxyFixture(certificates.serverContext, stage, response = "{\"partial\":true}").use { proxy ->
            configureProxy(proxy.port)
            val start = System.nanoTime()

            assertThatThrownBy { connector(limits.copy(responseTimeout = Duration.ofMillis(600))).collect(httpCollectionRequest()) }
                .isInstanceOf(CollectionConnectorException::class.java)
                .hasMessage("response timed out")

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1_800))
            assertThat(proxy.closed.get(1, TimeUnit.SECONDS)).matches {
                it == null ||
                    it.message.orEmpty().contains("reset", ignoreCase = true)
            }
        }
    }

    @Test
    fun `interruption closes the CONNECT socket and propagates interruption`() {
        InspectionProxyFixture(certificates.serverContext, InspectionProxyFixture.Stage.HEADERS_STALL).use { proxy ->
            configureProxy(proxy.port)
            val failure = CompletableFuture<Throwable>()
            val worker =
                thread(isDaemon = true) { runCatching { connector().collect(httpCollectionRequest()) }.onFailure { failure.complete(it) } }
            proxy.request.get(2, TimeUnit.SECONDS)

            worker.interrupt()

            assertThat(failure.get(1, TimeUnit.SECONDS)).isInstanceOf(InterruptedException::class.java)
            assertThat(proxy.closed.get(1, TimeUnit.SECONDS)).matches {
                it == null ||
                    it.message.orEmpty().contains("reset", ignoreCase = true)
            }
        }
    }

    @Test
    fun `proxy failure never falls back to the reachable direct destination`() {
        InspectionProxyFixture(certificates.serverContext, direct = true).use { destination ->
            InspectionProxyFixture(certificates.serverContext, InspectionProxyFixture.Stage.REJECT).use { proxy ->
                configureProxy(proxy.port)
                val client = HttpJsonCollectionConnector(limits, Clock.systemUTC(), { true }) { listOf(loopback) }

                assertThatThrownBy { client.collect(httpCollectionRequest("https://public.invalid:${destination.port}")) }
                    .isInstanceOf(CollectionConnectorException::class.java)
                    .hasMessage("unexpected HTTP status 403")
                assertThat(destination.request.isDone).isFalse()
            }
        }
    }

    @Test
    fun `the body limit is enforced through CONNECT`() {
        InspectionProxyFixture(certificates.serverContext, response = "\"${"x".repeat(2_000)}\"").use { proxy ->
            configureProxy(proxy.port)

            assertThatThrownBy { connector().collect(httpCollectionRequest()) }
                .isInstanceOf(CollectionConnectorException::class.java)
                .hasMessage("response body exceeds 1000 bytes")
        }
    }

    @Test
    fun `proxy DNS lookup shares the collection deadline`() {
        configureProxy(1)
        val resolver =
            HostResolver { host ->
                if (host == "proxy.invalid") Thread.sleep(2_000)
                listOf(publicAddress)
            }
        val client =
            HttpJsonCollectionConnector(
                limits.copy(responseTimeout = Duration.ofMillis(200)),
                Clock.systemUTC(),
                PublicAddressPolicy,
                resolver,
            )
        val start = System.nanoTime()

        assertThatThrownBy { client.collect(httpCollectionRequest()) }.hasMessage("destination host resolution timed out")

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1))
    }

    @Test
    fun `plain HTTP stays direct even when both JVM HTTP proxy properties are configured`() {
        configureProxy(1)
        System.setProperty("http.proxyHost", "proxy.invalid")
        System.setProperty("http.proxyPort", "1")
        val server = HttpServer.create(InetSocketAddress(loopback, 0), 0)
        server.createContext("/data") { exchange ->
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        server.start()
        try {
            val direct = HttpJsonCollectionConnector(limits, Clock.systemUTC(), { it == loopback }) { listOf(loopback) }

            assertThat(
                direct
                    .collect(httpCollectionRequest("http://public.invalid:${server.address.port}"))
                    .records
                    .single()
                    .payload,
            ).isEqualTo("{}")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `route is fixed before proxy DNS and is not reselected during the exchange`() {
        InspectionProxyFixture(certificates.serverContext).use { proxy ->
            configureProxy(proxy.port)
            val client =
                HttpJsonCollectionConnector(limits, Clock.systemUTC(), PublicAddressPolicy) { host ->
                    if (host == "proxy.invalid") {
                        System.setProperty("https.proxyPort", "1")
                        listOf(loopback)
                    } else {
                        listOf(publicAddress)
                    }
                }

            assertThat(
                client
                    .collect(httpCollectionRequest())
                    .records
                    .single()
                    .payload,
            ).isEqualTo("{}")
        }
    }

    @Test
    fun `proxy connection never fails over from its first pinned address`() {
        InspectionProxyFixture(certificates.serverContext).use { proxy ->
            configureProxy(proxy.port)
            val client =
                HttpJsonCollectionConnector(
                    limits.copy(connectTimeout = Duration.ofMillis(300)),
                    Clock.systemUTC(),
                    PublicAddressPolicy,
                ) { host ->
                    if (host == "proxy.invalid") listOf(InetAddress.getByName("127.0.0.2"), loopback) else listOf(publicAddress)
                }

            assertThatThrownBy { client.collect(httpCollectionRequest()) }
                .isInstanceOf(CollectionConnectorException::class.java)
                .message()
                .isIn("connection timed out", "HTTP request failed with org.apache.hc.client5.http.HttpHostConnectException")
            assertThat(proxy.request.isDone).isFalse()
        }
    }

    @Test
    fun `JAVA_TOOL_OPTIONS supplies CONNECT and PKCS12 trust in a fresh JVM`(
        @TempDir directory: Path,
    ) {
        InspectionProxyFixture(certificates.serverContext).use { proxy ->
            val log = directory.resolve("jvm-probe.log")
            val process =
                ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp",
                    testClasspath(),
                    JvmProxyCollectionProbe::class.java.name,
                    "https://public.invalid",
                ).redirectErrorStream(true).redirectOutput(log.toFile())
            process.environment()["JAVA_TOOL_OPTIONS"] =
                "-Dhttps.proxyHost=proxy.invalid -Dhttps.proxyPort=${proxy.port} -Dhttp.nonProxyHosts= " +
                "\"-Djavax.net.ssl.trustStore=${certificates.trustStorePath}\" " +
                "-Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=throwaway-test-only"
            val running = process.start()
            try {
                assertThat(running.waitFor(8, TimeUnit.SECONDS)).isTrue()
                assertThat(running.exitValue()).withFailMessage(Files.readString(log)).isZero()
                assertThat(proxy.request.get(1, TimeUnit.SECONDS).connect).isEqualTo("CONNECT public.invalid:443 HTTP/1.1")
            } finally {
                running.destroyForcibly()
            }
        }
    }

    private fun testClasspath(): String {
        val classes = listOf(javaClass, HttpJsonCollectionConnector::class.java)
        val roots = classes.map { it.protectionDomain.codeSource.location }
        val manifests = javaClass.classLoader.getResources("META-INF/MANIFEST.MF").toList()
        val jars = manifests.mapNotNull { (it.openConnection() as? JarURLConnection)?.jarFileURL }
        return (roots + jars).distinct().joinToString(File.pathSeparator) { Path.of(it.toURI()).toString() }
    }

    private fun connector(selectedLimits: HttpLimits = limits): HttpJsonCollectionConnector =
        HttpJsonCollectionConnector(selectedLimits, Clock.systemUTC(), PublicAddressPolicy) { host ->
            if (host == "proxy.invalid") listOf(loopback, loopback) else listOf(publicAddress)
        }

    private fun configureProxy(port: Int) {
        System.setProperty("https.proxyHost", "proxy.invalid")
        System.setProperty("https.proxyPort", port.toString())
        System.setProperty("http.nonProxyHosts", "")
    }

    companion object {
        private var generatedCertificates: InspectionCertificates? = null
        private val certificates: InspectionCertificates get() = checkNotNull(generatedCertificates)

        @JvmStatic
        @BeforeAll
        fun generateCertificates(
            @TempDir directory: Path,
        ) {
            generatedCertificates = InspectionCertificates(directory)
        }
    }
}
