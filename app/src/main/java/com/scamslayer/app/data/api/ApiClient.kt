package com.scamslayer.app.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"

    @Volatile
    private var currentBaseUrl: String = DEFAULT_BASE_URL

    @Volatile
    private var apiService: ApiService? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private const val APP_API_KEY = "sc5dYabxV-RvWYQNRKxGUT_qCQ9s-VKQx5Dmj7SvzC0"

    private val ngrokInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("ngrok-skip-browser-warning", "true")
            .addHeader("X-API-Key", APP_API_KEY)
            .build()
        chain.proceed(request)
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(ngrokInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getService(baseUrl: String? = null): ApiService {
        val url = baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"

        if (apiService == null || currentBaseUrl != normalizedUrl) {
            synchronized(this) {
                if (apiService == null || currentBaseUrl != normalizedUrl) {
                    currentBaseUrl = normalizedUrl
                    val retrofit = Retrofit.Builder()
                        .baseUrl(normalizedUrl)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                    apiService = retrofit.create(ApiService::class.java)
                }
            }
        }
        return apiService!!
    }

    fun getAudioUrl(recordingId: String, baseUrl: String? = null): String {
        val url = baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        val normalizedUrl = if (url.endsWith("/")) url.dropLast(1) else url
        return "$normalizedUrl/api/recordings/$recordingId/audio"
    }
}
