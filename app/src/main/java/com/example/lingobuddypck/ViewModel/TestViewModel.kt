package com.example.lingobuddypck.ViewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.Network.TogetherAI.AIGradingResult
import com.example.lingobuddypck.Network.TogetherAI.AIQuestionResponse
import com.example.lingobuddypck.Network.TogetherAI.ChatRequest
import com.example.lingobuddypck.Network.TogetherAI.ChatResponse
import com.example.lingobuddypck.Network.TogetherAI.Message
import com.example.lingobuddypck.Network.TogetherAI.QuestionData
import com.example.lingobuddypck.Network.TogetherAI.UserAnswer
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.awaitResponse

class TestViewModel : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _testQuestions = MutableLiveData<List<QuestionData>?>()
    val testQuestions: LiveData<List<QuestionData>?> = _testQuestions

    private val _gradingResult = MutableLiveData<AIGradingResult?>()
    val gradingResult: LiveData<AIGradingResult?> = _gradingResult

    private val _errorMesssage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMesssage

    private val gson = Gson() // Khởi tạo Gson hoặc JSON parser khác

    // Lời nhắn hệ thống cơ bản (có thể tùy chỉnh thêm)
    private val systemMessageForTestGeneration = Message(
        "system",
        "Bạn là một trợ lý AI chuyên tạo bài kiểm tra tiếng Anh."
    )
    private val systemMessageForGrading = Message(
        "system",
        "Bạn là một trợ lý AI chuyên chấm điểm bài kiểm tra tiếng Anh."
    )


    fun fetchTest(topic: String) {
        _isLoading.value = true
        _testQuestions.value = null // Xóa câu hỏi cũ
        _gradingResult.value = null // Xóa kết quả cũ
        _errorMesssage.value = null
        val randomType = listOf("Grammar", "Vocabulary")
        val type = randomType.random()

        viewModelScope.launch {
            try {
                val prompt ="""
                    Please generate a multiple-choice quiz with 5 questions on the topic "$type" "$topic" .Focus on English learning
                    Each question must have four options: a, b, c, and d.
                    For each question, provide:
                    - The question text
                    - The four answer choices
                    - The correct answer key (a, b, c, or d)
      
                    Make sure:
                    - All correct answers strictly follow modern English grammar.
                    - Tense usage, conditional structures, and logical context must be accurate.
                    - Absolutely no incorrect answers should be marked as correct.
                    
                    Return the result in **pure JSON format only**, without any explanations or extra text. Follow this structure exactly:
                    ```json
                    {
                      "questions": [
                        {
                          "id": "q1",
                          "question_text": "Question 1 content...",
                          "options": {
                            "a": "Option A content",
                            "b": "Option B content",
                            "c": "Option C content",
                            "d": "Option D content"
                          },
                          "correct_answer": "a"
                        },
                        {
                          "id": "q2",
                          "question_text": "Question 2 content...",
                          "options": { "a": "...", "b": "...", "c": "...", "d": "..." },
                          "correct_answer": "b"
                        },
                        {
                          "id": "q3",
                          "question_text": "Question 3 content...",
                          "options": { "a": "...", "b": "...", "c": "...", "d": "..." },
                          "correct_answer": "c"
                        },
                        {
                          "id": "q4",
                          "question_text": "Question 4 content...",
                          "options": { "a": "...", "b": "...", "c": "...", "d": "..." },
                          "correct_answer": "d"
                        },
                        {
                          "id": "q5",
                          "question_text": "Question 5 content...",
                          "options": { "a": "...", "b": "...", "c": "...", "d": "..." },
                          "correct_answer": "a"
                        }
                      ]
                    }
                    """.trimIndent()

                val messages = listOf(systemMessageForTestGeneration, Message("user", prompt))
                 val request = ChatRequest(model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free", messages = messages)

                val response = RetrofitClient.instance.chatWithAI(request).awaitResponse()
                val responseJson = response.body()?.output?.choices?.getOrNull(0)?.text
                Log.d("AI_JSON", "Extracted JSON: $responseJson")
                if (!responseJson.isNullOrBlank()) {
                    // Cố gắng trích xuất chỉ phần JSON nếu AI trả về thêm text thừa
                    val actualJson = extractJson(responseJson)
                    val aiResponse = gson.fromJson(actualJson, AIQuestionResponse::class.java)
                    if (aiResponse.questions.size == 5) {
                        _testQuestions.postValue(aiResponse.questions)
                    } else {
                        _errorMesssage.postValue("AI không trả về đủ 5 câu hỏi.")
                    }
                } else {
                    _errorMesssage.postValue("Không nhận được phản hồi từ AI hoặc phản hồi rỗng.")
                }

            } catch (e: Exception) {
                _errorMesssage.postValue("Lỗi khi tải bài test: ${e.message}")
                e.printStackTrace() // In lỗi ra logcat để debug
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun submitAnswers(userAnswers: List<UserAnswer>) {
        _isLoading.value = true
        _gradingResult.value = null
        _errorMesssage.value = null

        val currentQuestions = _testQuestions.value
        if (currentQuestions == null) {
            _errorMesssage.value = "Không có bài test để chấm điểm."
            _isLoading.value = false
            return
        }

        val userAnswersString = userAnswers.joinToString("; ") { answer ->
            val questionIndex = currentQuestions.indexOfFirst { it.id == answer.questionId }
            if (questionIndex != -1) {
                "${questionIndex + 1},${answer.selectedOptionKey}"
            } else {
                "${answer.questionId},${answer.selectedOptionKey}"
            }
        } + ";"

        viewModelScope.launch {
            try {
                val questionsJsonForGrading = gson.toJson(currentQuestions.map {
                    mapOf(
                        "id" to it.id,
                        "question_text" to it.question_text,
                        "options" to it.options,
                        "correct_answer" to it.correct_answer
                    )
                })

                val gradingPrompt = """
                Below is a multiple-choice quiz with correct answers and the user's answers.
                Please grade it and return how many answers are correct.

                Quiz with correct answers (in JSON format):
                ```json
                $questionsJsonForGrading
                ```

                User's answers (format: question_number,choice;):
                $userAnswersString

                Return a response in **pure JSON format**, no explanation, no extra text:
                ```json
                {
                  "score": [number_of_correct_answers],
                  "total_questions": 5,
                  "feedback": {
                    "q1": "correct",
                    "q2": "incorrect",
                    "q3": "correct",
                    ...
                  }
                }
                ```
            """.trimIndent()

                val messages = listOf(systemMessageForGrading, Message("user", gradingPrompt))
                val request = ChatRequest(
                    model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
                    messages = messages
                )

                val response = RetrofitClient.instance.chatWithAI(request).awaitResponse()
                val responseJson = response.body()?.output?.choices?.getOrNull(0)?.text
                Log.d("AI_GRADING_JSON", "Raw response: $responseJson")

                if (!responseJson.isNullOrBlank()) {
                    val actualJson = extractJson(responseJson)
                    val result = gson.fromJson(actualJson, AIGradingResult::class.java)
                    _gradingResult.postValue(result)
                } else {
                    _errorMesssage.postValue("Không nhận được phản hồi chấm điểm từ AI.")
                }

            } catch (e: Exception) {
                _errorMesssage.postValue("Lỗi khi chấm điểm: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    // Hàm tiện ích để cố gắng trích xuất JSON từ một chuỗi có thể chứa text thừa
    private fun extractJson(rawResponse: String): String {
        val jsonStartIndex = rawResponse.indexOfFirst { it == '{' || it == '[' }
        val jsonEndIndex = rawResponse.indexOfLast { it == '}' || it == ']' }

        return if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex > jsonStartIndex) {
            rawResponse.substring(jsonStartIndex, jsonEndIndex + 1)
        } else {
            // Nếu không tìm thấy JSON hợp lệ, trả về chuỗi rỗng hoặc ném lỗi
            // Để đơn giản, ở đây ta giả định AI luôn trả về JSON nếu thành công
            // Hoặc có thể bạn sẽ cần parse lỗi nếu AI trả về lỗi thay vì JSON
            rawResponse // Hoặc "" hoặc throw Exception("Invalid JSON response")
        }
    }

    fun clearErrorMessage() {
        _errorMesssage.value = null
    }
}