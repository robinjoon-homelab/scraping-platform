package com.example.template.collection.application.port.input

class CollectionPreviewFailedException(
    cause: Throwable,
) : RuntimeException("Preview collection failed", cause)
