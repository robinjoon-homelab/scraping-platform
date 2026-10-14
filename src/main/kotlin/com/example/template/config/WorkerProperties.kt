package com.example.template.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

// interval is the delay between the end of one cycle and the start of the next. batchSize bounds each step of a cycle
// (1..1000). leaseDuration must be longer than the connector's whole timeout, or a run still being collected is closed
// as expired.
@ConfigurationProperties("scraping.worker")
class WorkerProperties(
    val enabled: Boolean = true,
    val interval: Duration = Duration.ofSeconds(30),
    val batchSize: Int = 10,
    val leaseDuration: Duration = Duration.ofMinutes(5),
)
