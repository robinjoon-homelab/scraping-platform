package com.example.template.collection.adapter.outbound.http

import com.example.template.collection.application.port.output.CollectionConnectorException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.LockSupport

class BoundedHostResolverTest {
    private val release = CountDownLatch(1)
    private val address = InetAddress.getByName("8.8.8.8")

    // Ignores interruption like a lookup stuck in the operating system resolver.
    private val stuck =
        HostResolver {
            while (release.count > 0) LockSupport.parkNanos(Duration.ofMillis(10).toNanos())
            listOf(address)
        }

    @AfterEach
    fun releaseLookups() = release.countDown()

    @Test
    fun `a lookup within the timeout returns every address`() {
        val resolver = BoundedHostResolver({ listOf(address, InetAddress.getByName("1.1.1.1")) }, 1)

        assertThat(resolver.resolve("example.com", Duration.ofSeconds(1)))
            .containsExactly(address, InetAddress.getByName("1.1.1.1"))
    }

    @Test
    fun `an unknown host fails with a fixed reason`() {
        val resolver = BoundedHostResolver({ throw UnknownHostException("internal.example") }, 1)

        assertThatThrownBy { resolver.resolve("internal.example", Duration.ofSeconds(1)) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage("destination host could not be resolved")
    }

    @Test
    fun `a JVM error raised by the lookup reaches the caller as the same instance`() {
        val error = AssertionError("resolver broke")
        val resolver = BoundedHostResolver({ throw error }, 1)

        assertThatThrownBy { resolver.resolve("example.com", Duration.ofSeconds(1)) }.isSameAs(error)
    }

    @Test
    fun `an interruption raised inside the lookup reaches the caller as the same instance`() {
        val interruption = InterruptedException("lookup interrupted")
        val resolver = BoundedHostResolver({ throw interruption }, 1)

        assertThatThrownBy { resolver.resolve("example.com", Duration.ofSeconds(1)) }.isSameAs(interruption)
    }

    @Test
    fun `a stuck lookup times out, and once every thread is stuck new lookups fail at once`() {
        val resolver = BoundedHostResolver(stuck, 1)

        assertThatThrownBy { resolver.resolve("slow.example", Duration.ofMillis(50)) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage("destination host resolution timed out")
        assertThatThrownBy { resolver.resolve("other.example", Duration.ofSeconds(5)) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage("too many pending host resolutions")
    }

    @Test
    fun `no time left fails without a lookup`() {
        val resolver = BoundedHostResolver(stuck, 1)

        assertThatThrownBy { resolver.resolve("example.com", Duration.ZERO) }
            .isInstanceOf(CollectionConnectorException::class.java)
            .hasMessage("destination host resolution timed out")
    }
}
