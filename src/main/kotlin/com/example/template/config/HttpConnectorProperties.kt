package com.example.template.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

// Limits of the HTTP_JSON connector. responseTimeout is the deadline of a whole collection, covering both DNS address
// checks, connecting, the request and the body; it must stay well below scraping.worker.lease-duration (5m by default),
// or a run still being collected is closed as expired. maxBodyBytes stops reading a larger body and fails the run. Its
// default of 1,000,000 bytes keeps every accepted UTF-8 body within the record payload limit of 1,000,000 characters;
// a larger value only lets bigger bodies fail later as records that do not fit.
@ConfigurationProperties("scraping.connector.http")
class HttpConnectorProperties(
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val responseTimeout: Duration = Duration.ofSeconds(10),
    val maxBodyBytes: Int = 1_000_000,
)
