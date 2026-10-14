package com.example.template.collection.domain

class InvalidCollectionConfigurationException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
