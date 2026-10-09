package com.example.template.domain

class InvalidCollectionConfigurationException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
