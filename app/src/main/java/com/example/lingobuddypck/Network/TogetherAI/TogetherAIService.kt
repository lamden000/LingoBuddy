package com.example.lingobuddypck.Network.TogetherAI
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface TogetherApi {
    @Headers("Content-Type: application/json", "Authorization: Bearer 54b9de63b3f8a19573732caa41714fe6711816bf6ac33ceec867a26c6e8cd7e7")
    @POST("inference")
    fun chatWithAI(@Body request: ChatRequest): Call<ChatResponse>

    @POST("inference")
    fun chatWithImageAI(@Body request: ChatRequestImage): Call<ChatImageResponse>
}

data class ChatRequest(
    val model: String ,
    val messages: List<Message>,
    val max_tokens: Int = 300
)

data class ChatRequestImage(
    val model: String ,
    val messages: List<Map<String, Any>>,
    val max_tokens: Int = 300
)

data class Message(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
data class ChatResponse(
    val output: Output
)

data class ChatImageResponse(
    val choices: List<ChoiceImage>
)

data class ChoiceImage(
    val message: MessageContent,
    val logprobs: Any?,
    val finish_reason: String
)

data class MessageContent(
    val role: String,
    val content: String,
    val tool_calls: List<Any>
)

data class Output(
    val choices: List<Choice>
)

data class Choice(
    val text: String
)