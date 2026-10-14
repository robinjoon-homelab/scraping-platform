package com.example.template.collection.application.port.output

// A collection failure the connector has described. The message becomes the run's failure reason, so it must not contain
// response bodies or secrets. Any other exception from a connector fails the run with only its type name as the reason.
class CollectionConnectorException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
