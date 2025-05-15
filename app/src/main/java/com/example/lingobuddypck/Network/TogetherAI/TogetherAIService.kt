package com.example.lingobuddypck.Network.TogetherAI
import android.net.Uri
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface TogetherApi {
    @Headers("Content-Type: application/json", "Authorization: Bearer 54b9de63b3f8a19573732caa41714fe6711816bf6ac33ceec867a26c6e8cd7e7")
    @POST("inference")
    fun chatWithAI(@Body request: ChatRequest): Call<ChatResponse>

    @Headers("Content-Type: application/json", "Authorization: Bearer 54b9de63b3f8a19573732caa41714fe6711816bf6ac33ceec867a26c6e8cd7e7")
    @POST("inference")
    fun chatWithImageAI(@Body request: ChatRequestImage): Call<ChatImageResponse>
}

data class ChatRequest(
    val model: String ,
    val messages: List<Message>,
    val max_tokens: Int = 1000
)

data class Message(
    val role: String,
    val content: String? = null,
    val imageUri: Uri? = null,
    val imageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
data class ChatResponse(
    val output: Output
)

data class ChatImageResponse(
    val choices: List<ChoiceImage>
)

data class ChatRequestImage(
    val model: String ,
    val messages: List<Map<String, Any>>,
    val max_tokens: Int = 1000
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

//Test data
data class AIQuestionResponse(
    val questions: List<QuestionData>
)

data class QuestionData(
    val id: String,
    val question_text: String,
    val options: Map<String, String>, // {"a": "Text A", "b": "Text B", ...}
    val correct_answer: String // "a", "b", "c", hoặc "d"
)

// Dùng để lưu câu trả lời của người dùng
data class UserAnswer(
    val questionId: String,
    val selectedOptionKey: String // "a", "b", "c", hoặc "d"
)

// Dùng để parse JSON kết quả chấm điểm từ AI
data class AIGradingResult(
    val score: Int,
    val total_questions: Int,
    val feedback: Map<String, String>? // Optional: {"q1": "correct", "q2": "incorrect"}
)