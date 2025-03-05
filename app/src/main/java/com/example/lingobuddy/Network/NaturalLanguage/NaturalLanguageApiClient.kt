import com.example.lingobuddy.Network.NaturalLanguage.AnalyzeSentimentRequest
import com.example.lingobuddy.Network.NaturalLanguage.AnalyzeSentimentResponse
import retrofit2.Response
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
}