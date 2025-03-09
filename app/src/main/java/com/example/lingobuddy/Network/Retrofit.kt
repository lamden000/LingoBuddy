package com.example.lingobuddy.Network

import com.example.lingobuddy.Network.TogetherAI.TogetherApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://api.together.xyz/"

    val instance: TogetherApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TogetherApi::class.java)
    }
}