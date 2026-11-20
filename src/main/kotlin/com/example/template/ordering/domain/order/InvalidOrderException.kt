package com.example.template.ordering.domain.order

class InvalidOrderException(
    message: String,
) : IllegalArgumentException(message)
