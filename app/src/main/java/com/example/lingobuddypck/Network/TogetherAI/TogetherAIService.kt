package com.example.lingobuddypck.Network.TogetherAI
import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
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
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int = 2000,
    val temperature: Double = 1.3
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

@Parcelize // <-- Make sure this annotation is directly above the class
data class QuestionData(
    val id: String,
    val question_text: String,
    val options: Map<String, String>, // Standard types like String and Map<String, String> are usually fine with Parcelize
    val correct_answer: String
) : Parcelable

// Dùng để lưu câu trả lời của người dùng
data class UserAnswer(
    val questionId: String,
    val selectedOptionKey: String // "a", "b", "c", hoặc "d"
)

data class QuestionFeedback(
    val status: String, // Expected "correct" or "incorrect"
    val explanation: String? = null // This field is optional and will be null if not present (like for "correct" answers)
)
// Dùng để parse JSON kết quả chấm điểm từ AI
data class AIGradingResult(
    val score: Int,
    val total_questions: Int,
    val feedback: Map<String, QuestionFeedback> // Optional: {"q1": "correct", "q2": "incorrect"}
)

data class PassageQuizData(
    val passage: String,
    val questions: List<QuestionData>
)

data class PronunciationFeedback(
    val score: Double,
    val mistakes: List<String>,
    val suggestions: List<String>
)