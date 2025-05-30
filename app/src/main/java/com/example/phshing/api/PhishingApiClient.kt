package com.example.phshing.api

import com.example.phshing.api.PhishingApiService.Companion.BASE_URL
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Client for making API calls to the phishing detection service
 */
object PhishingApiClient {
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: PhishingApiService = retrofit.create(PhishingApiService::class.java)
    
    /**
     * Checks if a URL is a phishing site using the API
     * @param url The URL to check
     * @return A PhishingResult containing the confidence score and whether it's a phishing site
     */
    suspend fun checkUrl(url: String): PhishingResult {
        return try {
            val response = apiService.checkUrl(PhishingCheckRequest(url))
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                PhishingResult.Success(
                    confidence = body.confidence,
                    isPhishing = body.is_phishing,
                    message = body.message
                )
            } else {
                PhishingResult.Error("API error: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            PhishingResult.Error("Network error: ${e.message}")
        }
    }
}

/**
 * Sealed class representing the result of a phishing check
 */
sealed class PhishingResult {
    /**
     * Successful result with confidence score and phishing status
     */
    data class Success(
        val confidence: Double,
        val isPhishing: Boolean,
        val message: String
    ) : PhishingResult()
    
    /**
     * Error result with error message
     */
    data class Error(val message: String) : PhishingResult()
}
