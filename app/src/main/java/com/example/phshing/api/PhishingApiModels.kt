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

/**
 * Simplified result model for Layer 2 - Universal Link Hook functionality
 * This is used to simplify the handling of phishing detection results
 */
data class SimplifiedPhishingResult(
    val confidence: Double,
    val isPhishing: Boolean
)
