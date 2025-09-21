package com.example.lingobuddypck.Network

import com.example.lingobuddypck.BuildConfig
import com.example.lingobuddypck.Services.OpenAIAPI
import com.example.lingobuddypck.Services.TogetherApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://api.together.xyz/"
    private const val apiKey = BuildConfig.TOGETHERAI_API_KEY

    val instance: TogetherApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(60, TimeUnit.SECONDS) // Increase connection timeout
            .readTimeout(60, TimeUnit.SECONDS)    // Increase read timeout
            .writeTimeout(60, TimeUnit.SECONDS)   // Increase write timeout
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client) // Set the custom OkHttpClient
            .build()
            .create(TogetherApi::class.java)
    }
}

object RetrofitClientOpenAI {
    private const val BASE_URL = "https://api.openai.com/v1/"
    private const val apiKey = BuildConfig.OPENAI_API_KEY

    val instance: OpenAIAPI by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(60, TimeUnit.SECONDS) // Increase connection timeout
            .readTimeout(60, TimeUnit.SECONDS)    // Increase read timeout
            .writeTimeout(60, TimeUnit.SECONDS)   // Increase write timeout
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(OpenAIAPI::class.java)
    }
}