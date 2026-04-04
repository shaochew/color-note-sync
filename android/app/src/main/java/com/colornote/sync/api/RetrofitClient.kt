package com.colornote.sync.api

import android.content.Context
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Default points to host machine from Android emulator
    var baseUrl: String = "http://localhost:5001/"

    private var cachedUrl: String? = null
    private var cachedService: ApiService? = null

    val apiService: ApiService by lazy {
        createService()
    }

    private fun createService(): ApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /**
     * Get or create an ApiService using the server URL stored in SharedPreferences.
     * Rebuilds the service if the URL has changed since last call.
     */
    fun getService(context: Context): ApiService {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", "http://localhost:5001") ?: "http://localhost:5001"
        val normalizedUrl = if (savedUrl.endsWith("/")) savedUrl else "$savedUrl/"

        if (normalizedUrl == cachedUrl && cachedService != null) {
            return cachedService!!
        }

        return rebuild(normalizedUrl)
    }

    /**
     * Rebuild the service with a new base URL.
     */
    fun rebuild(newBaseUrl: String): ApiService {
        baseUrl = newBaseUrl
        cachedUrl = newBaseUrl
        val service = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        cachedService = service
        return service
    }
}
