package com.example.template.inventory.domain.stock

class InvalidStockException(
    message: String,
) : IllegalArgumentException(message)
