package com.example.lingobuddy.Network
/*import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface NaturalLanguageApi {
    @Headers("Content-Type: application/json")
    @POST("v1/documents:analyzeSentiment")
    suspend fun analyzeSentiment(
        @Query("key") apiKey: String,
        @Body request: AnalyzeSentimentRequest
    ): Response<AnalyzeSentimentResponse>
}*/


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