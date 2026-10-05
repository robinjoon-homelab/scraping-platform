package com.example.template.config

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=jdbc:h2:mem:virtual-thread-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
@Import(VirtualThreadTest.ProbeConfiguration::class)
class VirtualThreadTest {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var probe: RequestThreadProbe

    @Test
    fun `embedded server handles real HTTP requests on virtual threads`() {
        val request =
            HttpRequest
                .newBuilder(URI("http://localhost:$port/actuator/health"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

        val response =
            HttpClient.newHttpClient().use { client ->
                client.send(request, HttpResponse.BodyHandlers.ofString())
            }

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(probe.observedVirtualThread.get(10, TimeUnit.SECONDS)).isTrue()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ProbeConfiguration {
        @Bean
        fun requestThreadProbe() = RequestThreadProbe()
    }

    class RequestThreadProbe : Filter {
        val observedVirtualThread = CompletableFuture<Boolean>()

        override fun doFilter(
            request: ServletRequest,
            response: ServletResponse,
            chain: FilterChain,
        ) {
            observedVirtualThread.complete(Thread.currentThread().isVirtual)
            chain.doFilter(request, response)
        }
    }
}
