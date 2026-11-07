package com.example.template.collection.adapter.outbound.http

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.file.Path
import java.security.KeyStore
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.thread
import kotlin.io.path.inputStream

// Local inspection endpoint: it accepts CONNECT and presents a throwaway certificate for the original destination.
// It deliberately does not resolve the destination; upstream DNS/public-address enforcement belongs to the real proxy.
internal class InspectionProxyFixture(
    private val context: SSLContext,
    private val stage: Stage = Stage.RESPONSE,
    private val response: String = "{}",
    private val direct: Boolean = false,
) : AutoCloseable {
    enum class Stage { RESPONSE, CONNECT_STALL, TLS_STALL, HEADERS_STALL, BODY_STALL, REJECT }

    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    private val sockets = CopyOnWriteArrayList<Socket>()
    val port: Int = server.localPort
    val request = CompletableFuture<ObservedRequest>()
    val closed = CompletableFuture<SocketException?>()

    init {
        thread(isDaemon = true, name = "test-inspection-proxy") {
            runCatching { serve(server.accept().also { sockets.add(it) }) }
                .onFailure {
                    request.completeExceptionally(it)
                    closed.completeExceptionally(it)
                }
        }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 5_000
        val connect = if (direct) emptyList() else headers(socket)
        if (stage == Stage.CONNECT_STALL) return drain(socket)
        if (stage == Stage.REJECT) {
            socket.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n".toByteArray())
            socket.close()
            return
        }
        if (!direct) socket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
        if (stage == Stage.TLS_STALL) return drain(socket)
        val tls = checkNotNull(context.socketFactory.createSocket(socket, "public.invalid", port, true)) as SSLSocket
        sockets.add(tls)
        tls.useClientMode = false
        tls.startHandshake()
        val received = headers(tls)
        val session = checkNotNull(tls.session) as ExtendedSSLSession
        request.complete(
            ObservedRequest(
                connect.firstOrNull(),
                received,
                session.requestedServerNames.filterIsInstance<SNIHostName>().map {
                    it.asciiName
                },
            ),
        )
        respond(tls)
    }

    private fun respond(tls: Socket) {
        if (stage == Stage.HEADERS_STALL) return drain(tls)
        val bytes = response.toByteArray()
        tls.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\n\r\n".toByteArray())
        if (stage == Stage.BODY_STALL) {
            tls.getOutputStream().write(bytes.copyOf(1))
            tls.getOutputStream().flush()
            return drain(tls)
        }
        tls.getOutputStream().write(bytes)
        tls.getOutputStream().flush()
        tls.close()
    }

    private fun drain(socket: Socket) {
        val buffer = ByteArray(1_024)
        try {
            var read = socket.getInputStream().read(buffer)
            while (read > 0) read = socket.getInputStream().read(buffer)
        } catch (exception: SocketException) {
            // Closing a socket with unread CONNECT/TLS bytes may reset TCP instead of returning EOF.
            closed.complete(exception)
            return
        }
        closed.complete(null)
    }

    // Read headers bytewise so CONNECT never consumes bytes belonging to the following TLS handshake.
    private fun headers(socket: Socket): List<String> {
        val text = StringBuilder()
        while (!text.endsWith("\r\n\r\n") && text.length < 16_384) {
            val next = socket.getInputStream().read()
            check(next >= 0) { "peer closed before headers" }
            text.append(next.toChar())
        }
        return text.toString().trimEnd().split("\r\n")
    }

    override fun close() {
        server.close()
        sockets.forEach(Socket::close)
    }

    class ObservedRequest(
        val connect: String?,
        val headers: List<String>,
        val sni: List<String>,
    )
}

internal class InspectionCertificates(
    private val directory: Path,
) {
    private val password = "throwaway-test-only".toCharArray()
    private val trusted = generate("trusted")
    val serverContext: SSLContext = serverContext(trusted)
    val untrustedContext: SSLContext = serverContext(generate("untrusted"))
    val trustStorePath: Path = directory.resolve("trust.p12")
    val clientContext: SSLContext

    init {
        val store = KeyStore.getInstance("PKCS12")
        store.load(null, password)
        store.setCertificateEntry("inspection", trusted.getCertificate("server"))
        java.nio.file.Files
            .newOutputStream(trustStorePath)
            .use { store.store(it, password) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trust.init(store)
        clientContext = SSLContext.getInstance("TLS")
        clientContext.init(null, trust.trustManagers, null)
    }

    private fun generate(name: String): KeyStore {
        val path = directory.resolve("$name.p12")
        val process =
            ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair",
                "-alias",
                "server",
                "-keystore",
                path.toString(),
                "-storetype",
                "PKCS12",
                "-storepass",
                String(password),
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "2",
                "-dname",
                "CN=public.invalid",
                "-ext",
                "SAN=dns:public.invalid",
                "-noprompt",
            ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output }
        val store = KeyStore.getInstance("PKCS12")
        path.inputStream().use { store.load(it, password) }
        return store
    }

    private fun serverContext(store: KeyStore): SSLContext {
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keys.init(store, password)
        val context = SSLContext.getInstance("TLS")
        context.init(keys.keyManagers, null, null)
        return context
    }
}
