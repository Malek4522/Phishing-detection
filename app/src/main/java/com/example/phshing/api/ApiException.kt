package com.example.phshing.api

/**
 * Custom exception for API errors with error type
 */
class ApiException(message: String, val errorType: ErrorType) : Exception(message)
