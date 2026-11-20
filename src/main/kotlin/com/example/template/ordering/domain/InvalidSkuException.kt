package com.example.template.ordering.domain

class InvalidSkuException(
    message: String,
) : IllegalArgumentException(message)
