package com.example.lingobuddy.Network.TogetherAI
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.UUID

interface TogetherApi {
    @Headers("Content-Type: application/json", "Authorization: Bearer 54b9de63b3f8a19573732caa41714fe6711816bf6ac33ceec867a26c6e8cd7e7")
    @POST("inference")
    fun chatWithAI(@Body request: ChatRequest): Call<ChatResponse>
}

data class ChatRequest(
    val model: String = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
    val messages: List<Message>,
    val max_tokens: Int = 300
)
enum class MessageType {
    TEXT, IMAGE, AUDIO
}
data class Message(
    val role: String,
    val content: String,
    val type: MessageType = MessageType.TEXT, // Mặc định là TEXT
    val timestamp: Long = System.currentTimeMillis()
)
data class ChatResponse(
    val output: Output
)

data class Output(
    val choices: List<Choice>
)

data class Choice(
    val text: String
)