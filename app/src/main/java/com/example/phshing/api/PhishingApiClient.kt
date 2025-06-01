package com.example.phshing.api

import android.util.Log
import com.example.phshing.api.PhishingApiService.Companion.BASE_URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Client for making API calls to the phishing detection service
 */
object PhishingApiClient {
    private const val TAG = "PhishingApiClient"
    private const val API_TIMEOUT_MS = 15000L // 15 seconds timeout
    
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
        return withContext(Dispatchers.IO) {
            try {
                // Apply timeout to the API call
                withTimeout(API_TIMEOUT_MS) {
                    val response = apiService.checkUrl(PhishingCheckRequest(url))
                    
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        Log.d(TAG, "API success for URL: $url, isPhishing: ${body.is_phishing}, confidence: ${body.confidence}")
                        PhishingResult.Success(
                            confidence = body.confidence,
                            isPhishing = body.is_phishing,
                            message = body.message
                        )
                    } else {
                        val errorMsg = "API error: ${response.code()} ${response.message()}"
                        Log.e(TAG, errorMsg)
                        PhishingResult.Error(errorMsg, ErrorType.API_ERROR)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "API timeout for URL: $url", e)
                PhishingResult.Error("Request timed out. Please try again later.", ErrorType.TIMEOUT)
            } catch (e: CancellationException) {
                Log.e(TAG, "API request cancelled for URL: $url", e)
                PhishingResult.Error("Request was cancelled.", ErrorType.CANCELLED)
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Socket timeout for URL: $url", e)
                PhishingResult.Error("Connection timed out. Please check your internet connection.", ErrorType.TIMEOUT)
            } catch (e: UnknownHostException) {
                Log.e(TAG, "Unknown host for URL: $url", e)
                PhishingResult.Error("Cannot reach server. Please check your internet connection.", ErrorType.NETWORK)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error for URL: $url", e)
                PhishingResult.Error("Network error: ${e.message}", ErrorType.UNKNOWN)
            }
        }
    }
    
    /**
     * Scans a URL for phishing and returns a simplified result object
     * This is used by the Layer 2 - Universal Link Hook functionality
     * 
     * @param url The URL to scan
     * @param cancelOnError If true, throws an exception on error; if false, returns a default safe result
     * @return A simplified result with confidence and phishing status
     * @throws Exception if the API call fails and cancelOnError is true
     */
    suspend fun scanUrl(url: String, cancelOnError: Boolean = true): SimplifiedPhishingResult {
        val result = checkUrl(url)
        
        return when (result) {
            is PhishingResult.Success -> {
                SimplifiedPhishingResult(
                    confidence = result.confidence,
                    isPhishing = result.isPhishing
                )
            }
            is PhishingResult.Error -> {
                if (cancelOnError) {
                    throw ApiException(result.message, result.errorType)
                } else {
                    // Return a default safe result when errors shouldn't cancel the operation
                    Log.w(TAG, "Returning safe default result due to error: ${result.message}")
                    SimplifiedPhishingResult(
                        confidence = 0.0,
                        isPhishing = false
                    )
                }
            }
        }
    }
}

// ErrorType enum and ApiException class have been moved to separate files

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
     * Error result with error message and type
     */
    data class Error(
        val message: String,
        val errorType: ErrorType = ErrorType.UNKNOWN
    ) : PhishingResult()
}
