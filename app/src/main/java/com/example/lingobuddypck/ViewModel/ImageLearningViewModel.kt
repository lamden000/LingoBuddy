package com.example.lingobuddypck.ViewModel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.Network.RetrofitClientOpenAI
import com.example.lingobuddypck.Services.ChatImageResponse
import com.example.lingobuddypck.Services.ChatRequest
import com.example.lingobuddypck.Services.ChatRequestImage
import com.example.lingobuddypck.Services.ChatResponse
import com.example.lingobuddypck.Services.Message
import com.example.lingobuddypck.data.ImageQuiz
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ImageLearningViewModel : ViewModel() {

    private val _chatMessages = MutableLiveData<List<Message>>()
    val chatMessages: LiveData<List<Message>> = _chatMessages

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _currentQuiz = MutableLiveData<ImageQuiz?>()
    val currentQuiz: LiveData<ImageQuiz?> = _currentQuiz

    private val _isGeneratingQuiz = MutableLiveData<Boolean>()
    val isGeneratingQuiz: LiveData<Boolean> = _isGeneratingQuiz

    private val _quizScore = MutableLiveData<Pair<Int, Int>?>() // Pair of (score, total)
    val quizScore: LiveData<Pair<Int, Int>?> = _quizScore

    private val chatHistory = mutableListOf<Message>()
    private val MAX_CHAT_HISTORY = 5
    val isWaitingForResponse = MutableLiveData<Boolean>(false)
    val imageModel ="meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8"

    data class QuizFeedback(
        val status: String, // "correct" or "incorrect"
        val explanation: String? = null // Only present for incorrect answers
    )

    data class QuizResult(
        val score: Int,
        val totalQuestions: Int,
        val feedback: Map<String, QuizFeedback>
    )

    private val _quizResult = MutableLiveData<QuizResult?>()
    val quizResult: LiveData<QuizResult?> = _quizResult

    init {
        val aiMessage = Message(
            content = "Xin chào, tôi có thể giúp gì cho bạn?",
            role = "assistant",
            imageUri = null
        )
        val currentMessages = _chatMessages.value.orEmpty().toMutableList()
        currentMessages.add(aiMessage)
        _chatMessages.value = currentMessages
    }

    fun sendImageAndMessage(context: Context, message: String, imageUri: Uri?) {
        _loading.value = true

        viewModelScope.launch { // This is your coroutine scope
            try {
                val contentList = mutableListOf<Map<String, Any>>()
                val currentMessages = _chatMessages.value.orEmpty().toMutableList()
                var base64ImageForUserMessage: String? = null

                if (message.isNotEmpty()) {
                    contentList.add(mapOf("type" to "text", "text" to message))
                }

                if (imageUri != null) {
                    base64ImageForUserMessage = encodeImageToBase64(context, imageUri)
                    contentList.add(
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf("url" to base64ImageForUserMessage)
                        )
                    )
                }

                val userMessage = Message(
                    content = message,
                    role = "user",
                    imageUri = imageUri,
                    imageUrl = base64ImageForUserMessage
                )

                currentMessages.add(userMessage)
                _chatMessages.value = currentMessages

                // Prepare the messages for the request, including chat history
                val messagesForRequest = mutableListOf<Map<String, Any>>()

                // Add past messages from history to the request
                chatHistory.forEach { msg ->
                    val historyContentList = mutableListOf<Map<String, Any>>()
                    msg.content?.let {
                        historyContentList.add(mapOf("type" to "text", "text" to it))
                    }
                    msg.imageUrl?.let {
                        historyContentList.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to it)))
                    }
                    messagesForRequest.add(mapOf("role" to msg.role, "content" to historyContentList))
                }

                // Add the current user message to the request
                messagesForRequest.add(mapOf("role" to "user", "content" to contentList))

                val request = ChatRequestImage(
                    model = imageModel,
                    messages = messagesForRequest,
                    max_tokens = 1000
                )

                ///////
                val gson = GsonBuilder().setPrettyPrinting().create()

                val requestForLogging = request.copy() // giữ nguyên request thật để gửi

// Tạo phiên bản log-safe
                val messagesForLogging = requestForLogging.messages.map { msg ->
                    val contentList = msg["content"] as? MutableList<Map<String, Any>> ?: mutableListOf()
                    val contentListMasked = contentList.map { contentMap ->
                        if (contentMap["type"] == "image_url") {
                            // Thay base64 bằng placeholder
                            mapOf("type" to "image_url", "image_url" to mapOf("url" to "--- base64 removed ---"))
                        } else contentMap
                    }
                    mapOf("role" to msg["role"], "content" to contentListMasked)
                }

                val jsonMasked = gson.toJson(
                    mapOf(
                        "model" to requestForLogging.model,
                        "max_tokens" to requestForLogging.max_tokens,
                        "messages" to messagesForLogging
                    )
                )

                Log.d("API_REQUEST_SAFE", jsonMasked)
                //////

                isWaitingForResponse.value=true
                // Call the API (using enqueue for Retrofit, which handles its own threading)
                RetrofitClient.instance.chatWithImageAI(request).enqueue(object : Callback<ChatImageResponse> {
                    override fun onResponse(call: Call<ChatImageResponse>, response: Response<ChatImageResponse>) {
                        _loading.value = false
                        isWaitingForResponse.value=false
                        if (response.isSuccessful) {
                            val responseContent = response.body()?.choices?.get(0)?.message?.content ?: "No response from AI."

                            val aiMessage = Message(
                                content = responseContent,
                                role = "assistant",
                                imageUri = null
                            )

                            // Update the LiveData with the new list of messages
                            val currentMessages = _chatMessages.value.orEmpty().toMutableList()
                            currentMessages.add(aiMessage)
                            _chatMessages.value = currentMessages

                            // Update chat history
                            chatHistory.add(userMessage)
                            chatHistory.add(aiMessage)

                            // Trim history
                            if (chatHistory.size > MAX_CHAT_HISTORY * 2) {
                                chatHistory.subList(0, chatHistory.size - MAX_CHAT_HISTORY * 2).clear()
                            }

                        } else {
                            _loading.value = false
                            _chatMessages.value = listOf(Message( "AI","Error: ${response.message()}"))
                        }
                    }

                    override fun onFailure(call: Call<ChatImageResponse>, t: Throwable) {
                        _loading.value = false
                        isWaitingForResponse.value=false
                        _chatMessages.value = listOf(Message("AI", "Error: ${t.message}"))
                        Log.e("API_ERROR", "API call failed", t)
                    }
                })
            } catch (e: Exception) {
                _loading.value = false
                isWaitingForResponse.value=false
                _chatMessages.value = listOf(Message("AI", "Error encoding image: ${e.message}"))
                Log.e("IMAGE_ENCODING", "Image encoding failed", e)
            }
        }
    }

    private suspend fun encodeImageToBase64(context: Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return@withContext "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun generateQuizFromImage(context: Context, imageUri: Uri) {
        _isGeneratingQuiz.value = true
        _currentQuiz.value = null
        _quizScore.value = null
        isWaitingForResponse.value = true
        _loading.value = true

        viewModelScope.launch {
            try {
                val base64Image = encodeImageToBase64(context, imageUri)
                val descriptionPrompt = """
                    Please describe this image in detail, focusing on:
                    1. The main objects, people, or scene visible
                    2. Any actions or activities taking place
                    3. Notable features, colors, or arrangements
                    4. The overall context or setting
                    
                    Provide a comprehensive but concise description that could be used for English learning purposes.
                """.trimIndent()

                // Prepare the vision request
                val visionContentList = mutableListOf<Map<String, Any>>(
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to base64Image)),
                    mapOf("type" to "text", "text" to descriptionPrompt)
                )

                val visionRequest = ChatRequestImage(
                    model = imageModel,
                    messages = listOf(
                        mapOf("role" to "user", "content" to visionContentList)
                    )
                )

                // Make the first API call for image description
                RetrofitClient.instance.chatWithImageAI(visionRequest).enqueue(object : Callback<ChatImageResponse> {
                    override fun onResponse(call: Call<ChatImageResponse>, response: Response<ChatImageResponse>) {
                        if (response.isSuccessful) {
                            val imageDescription = response.body()?.choices?.get(0)?.message?.content
                                ?: throw Exception("No description content")

                            val quizPrompt = """
                                Using this image description, create an English learning quiz:
                                
                                Image Description: $imageDescription
                                
                                Create exactly 5 questions that:
                                1. Test vocabulary related to objects/actions described
                                2. Test grammar points that could be used to describe the scene
                                3. Test comprehension about the situation
                                4. Include at least one question about cultural aspects if relevant
                                
                                Return ONLY a JSON object in this exact format, with no additional text:
                                {
                                    "imageDescription": "$imageDescription",
                                    "questions": [
                                        {
                                            "id": "q1",
                                            "question": "Question text",
                                            "options": {
                                                "a": "Option A",
                                                "b": "Option B",
                                                "c": "Option C",
                                                "d": "Option D"
                                            },
                                            "correctAnswer": "a",
                                            "explanation": "Why this answer is correct"
                                        }
                                    ]
                                }
                            """.trimIndent()

                            val quizRequest = ChatRequest(
                                model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
                                messages = listOf(
                                    Message(role = "user", content = quizPrompt)
                                )
                            )

                            RetrofitClient.instance.chatWithAI(quizRequest).enqueue(object : Callback<ChatResponse> {
                                override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                                    if (response.isSuccessful) {
                                        try {
                                            isWaitingForResponse.value = false
                                            _loading.value = false
                                            val quizContent = response.body()?.output?.choices?.get(0)?.text
                                                ?: throw Exception("No quiz content")

                                            Log.d("ImageQuizGeneration", "Quiz response: $quizContent")

                                            // Extract JSON from the response
                                            val jsonPattern = """\{[\s\S]*\}""".toRegex()
                                            val jsonMatch = jsonPattern.find(quizContent)
                                            val jsonString = jsonMatch?.value ?: throw Exception("No JSON found in response")

                                            val quiz = Gson().fromJson(jsonString, ImageQuiz::class.java)
                                            _currentQuiz.postValue(quiz)
                                        } catch (e: Exception) {
                                            Log.e("ImageQuizGeneration", "Failed to parse quiz response", e)
                                            _currentQuiz.postValue(null)
                                        }
                                    } else {
                                        Log.e("ImageQuizGeneration", "Quiz API call failed: ${response.errorBody()?.string()}")
                                        _currentQuiz.postValue(null)
                                    }
                                    _isGeneratingQuiz.postValue(false)
                                }

                                override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                                    Log.e("ImageQuizGeneration", "Quiz API call failed", t)
                                    _currentQuiz.postValue(null)
                                    _isGeneratingQuiz.postValue(false)
                                    isWaitingForResponse.value = false
                                    _loading.value = false
                                }
                            })
                        } else {
                            Log.e("ImageQuizGeneration", "Description API call failed: ${response.errorBody()?.string()}")
                            _currentQuiz.postValue(null)
                            _isGeneratingQuiz.postValue(false)
                            isWaitingForResponse.value = false
                            _loading.value = false
                        }
                    }

                    override fun onFailure(call: Call<ChatImageResponse>, t: Throwable) {
                        Log.e("ImageQuizGeneration", "Description API call failed", t)
                        _currentQuiz.postValue(null)
                        _isGeneratingQuiz.postValue(false)
                        isWaitingForResponse.value = false
                        _loading.value = false
                    }
                })
            } catch (e: Exception) {
                Log.e("ImageQuizGeneration", "Failed to generate quiz", e)
                _currentQuiz.postValue(null)
                _isGeneratingQuiz.postValue(false)
                isWaitingForResponse.value = false
                _loading.value = false
            }
        }
    }

    fun submitQuizAnswers(answers: Map<String, String>) {
        val quiz = _currentQuiz.value ?: return
        var correctAnswers = 0
        val feedback = mutableMapOf<String, QuizFeedback>()
        
        quiz.questions.forEach { question ->
            val userAnswer = answers[question.id]
            val isCorrect = userAnswer == question.correctAnswer
            
            if (isCorrect) {
                correctAnswers++
                feedback[question.id] = QuizFeedback(status = "correct")
            } else {
                feedback[question.id] = QuizFeedback(
                    status = "incorrect",
                    explanation = question.explanation
                )
            }
        }
        
        _quizResult.value = QuizResult(
            score = correctAnswers,
            totalQuestions = quiz.questions.size,
            feedback = feedback
        )
        _quizScore.value = Pair(correctAnswers, quiz.questions.size)
    }

    fun clearQuiz() {
        _currentQuiz.value = null
        _quizScore.value = null
        _quizResult.value = null
    }
}