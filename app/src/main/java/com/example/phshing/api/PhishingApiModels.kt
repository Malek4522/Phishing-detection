package com.example.phshing.api

/**
 * Request model for the phishing detection API
 */
data class PhishingCheckRequest(
    val url: String
)

/**
 * Response model from the phishing detection API
 */
data class PhishingCheckResponse(
    val confidence: Double,
    val is_phishing: Boolean,
    val message: String
)
