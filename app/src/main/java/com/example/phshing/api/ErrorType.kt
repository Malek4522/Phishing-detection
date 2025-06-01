package com.example.phshing.api

/**
 * Enum representing different types of API errors
 */
enum class ErrorType {
    API_ERROR,    // Server returned an error response
    NETWORK,      // Network connectivity issues
    TIMEOUT,      // Request timed out
    CANCELLED,    // Request was cancelled
    UNKNOWN       // Unknown or unexpected error
}
