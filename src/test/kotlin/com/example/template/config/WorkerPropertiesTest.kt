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

class WorkerPropertiesTest {
    @Test
    fun `unset properties bind to the documented defaults`() {
        val properties = bind(Binder(MapConfigurationPropertySource(emptyMap<String, String>())))

        assertThat(properties.enabled).isTrue()
        assertThat(properties.interval).isEqualTo(Duration.ofSeconds(30))
        assertThat(properties.batchSize).isEqualTo(10)
        assertThat(properties.leaseDuration).isEqualTo(Duration.ofMinutes(5))
    }

    @Test
    fun `application yaml carries the same defaults`() {
        val sources = YamlPropertySourceLoader().load("application", ClassPathResource("application.yaml"))

        val properties = bind(Binder(ConfigurationPropertySources.from(sources)))

        assertThat(properties.enabled).isTrue()
        assertThat(properties.interval).isEqualTo(Duration.ofSeconds(30))
        assertThat(properties.batchSize).isEqualTo(10)
        assertThat(properties.leaseDuration).isEqualTo(Duration.ofMinutes(5))
    }

    @Test
    fun `configured values override the defaults`() {
        val source =
            MapConfigurationPropertySource(
                mapOf(
                    "scraping.worker.enabled" to "false",
                    "scraping.worker.interval" to "10s",
                    "scraping.worker.batch-size" to "50",
                    "scraping.worker.lease-duration" to "2m",
                ),
            )

        val properties = bind(Binder(source))

        assertThat(properties.enabled).isFalse()
        assertThat(properties.interval).isEqualTo(Duration.ofSeconds(10))
        assertThat(properties.batchSize).isEqualTo(50)
        assertThat(properties.leaseDuration).isEqualTo(Duration.ofMinutes(2))
    }

    private fun bind(binder: Binder): WorkerProperties = binder.bindOrCreate("scraping.worker", Bindable.of(WorkerProperties::class.java))
}
