package com.example.template.collection.adapter.outbound.http

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ExchangeSocketsTest {
    private val server = ServerSocket(0, 0, InetAddress.getLoopbackAddress())
    private val readers = Executors.newSingleThreadExecutor()

    @AfterEach
    fun stop() {
        readers.shutdownNow()
        server.close()
    }

    @Test
    fun `a socket created after cancel is closed before it can connect`() {
        val sockets = ExchangeSockets()
        sockets.cancel()

        val socket = sockets.create(null)

        assertThat(socket.isClosed).isTrue()
        assertThatThrownBy { socket.connect(server.localSocketAddress, 1_000) }.isInstanceOf(SocketException::class.java)
    }

    @Test
    fun `cancel ends a blocked read at once and the peer sees the end of the stream`() {
        val sockets = ExchangeSockets()
        val socket = sockets.create(null).apply { connect(server.localSocketAddress, 1_000) }
        val peer = server.accept().apply { soTimeout = 1_000 }
        val blockedRead = readers.submit<Int> { socket.getInputStream().read() }

        val start = System.nanoTime()
        sockets.cancel()

        assertThatThrownBy { blockedRead.get(1, TimeUnit.SECONDS) }
            .isInstanceOf(ExecutionException::class.java)
            .hasCauseInstanceOf(SocketException::class.java)
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1))
        assertThat(peer.getInputStream().read()).isEqualTo(-1)
        peer.close()
    }
}
