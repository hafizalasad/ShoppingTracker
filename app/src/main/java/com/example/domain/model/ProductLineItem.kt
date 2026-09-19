package com.example.domain.model

data class ProductLineItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val amount: Double = 0.0,
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val category: String = "General"
)
