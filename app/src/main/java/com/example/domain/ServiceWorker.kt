package com.example.domain

data class ServiceWorker(
    val id: String,
    val name: String,
    val roleTamil: String,
    val rating: Float,
    val pastWorkImages: List<String>
)
