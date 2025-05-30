package com.example.phshing.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for the phishing detection API
 */
interface PhishingApiService {
    
    @POST("predict")
    suspend fun checkUrl(@Body request: PhishingCheckRequest): Response<PhishingCheckResponse>
    
    companion object {
        const val BASE_URL = "https://site-phishing.onrender.com/"
    }
}
