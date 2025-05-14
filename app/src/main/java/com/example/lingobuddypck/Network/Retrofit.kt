package com.example.lingobuddypck.Network

import com.example.lingobuddypck.Network.TogetherAI.TogetherApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://api.together.xyz/"

    val instance: TogetherApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // Increase connection timeout
            .readTimeout(30, TimeUnit.SECONDS)    // Increase read timeout
            .writeTimeout(30, TimeUnit.SECONDS)   // Increase write timeout
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client) // Set the custom OkHttpClient
            .build()
            .create(TogetherApi::class.java)
    }
}