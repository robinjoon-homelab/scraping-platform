package com.example.template.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import java.time.Duration

class HttpConnectorPropertiesTest {
    @Test
    fun `unset properties bind to the documented defaults`() {
        val properties = bind(Binder(MapConfigurationPropertySource(emptyMap<String, String>())))

        assertThat(properties.connectTimeout).isEqualTo(Duration.ofSeconds(5))
        assertThat(properties.responseTimeout).isEqualTo(Duration.ofSeconds(10))
        assertThat(properties.maxBodyBytes).isEqualTo(1_000_000)
    }

    @Test
    fun `application yaml carries the same defaults`() {
        val sources = YamlPropertySourceLoader().load("application", ClassPathResource("application.yaml"))

        val properties = bind(Binder(ConfigurationPropertySources.from(sources)))

        assertThat(properties.connectTimeout).isEqualTo(Duration.ofSeconds(5))
        assertThat(properties.responseTimeout).isEqualTo(Duration.ofSeconds(10))
        assertThat(properties.maxBodyBytes).isEqualTo(1_000_000)
    }

    @Test
    fun `configured values override the defaults`() {
        val source =
            MapConfigurationPropertySource(
                mapOf(
                    "scraping.connector.http.connect-timeout" to "2s",
                    "scraping.connector.http.response-timeout" to "30s",
                    "scraping.connector.http.max-body-bytes" to "1000",
                ),
            )

        val properties = bind(Binder(source))

        assertThat(properties.connectTimeout).isEqualTo(Duration.ofSeconds(2))
        assertThat(properties.responseTimeout).isEqualTo(Duration.ofSeconds(30))
        assertThat(properties.maxBodyBytes).isEqualTo(1000)
    }

    private fun bind(binder: Binder): HttpConnectorProperties =
        binder.bindOrCreate("scraping.connector.http", Bindable.of(HttpConnectorProperties::class.java))
}
