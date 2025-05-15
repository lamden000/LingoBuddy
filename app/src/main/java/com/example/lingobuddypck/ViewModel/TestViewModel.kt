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


    fun fetchTest(topic: String,isCustom:Boolean) {
        _isLoading.value = true
        _testQuestions.value = null // Xóa câu hỏi cũ
        _gradingResult.value = null // Xóa kết quả cũ
        _errorMesssage.value = null
        val randomType = listOf("Grammar", "Vocabulary")
        val type = randomType.random()

        viewModelScope.launch {
            try {
                val prompt ="""
                    Please generate a multiple-choice quiz with 10 questions on the topic "$type" "$topic" .Focus on English learning
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
                        ....
                      ]
                    }
                    """.trimIndent()

                val messages = listOf(systemMessageForTestGeneration, Message("user", prompt))
                val model:String;
                if(isCustom)
                {model="deepseek-ai/DeepSeek-R1-Distill-Llama-70B-free"}
                else{model="meta-llama/Llama-3.3-70B-Instruct-Turbo-Free"}
                 val request = ChatRequest(model, messages = messages)

                val response = RetrofitClient.instance.chatWithAI(request).awaitResponse()
                val responseJson = response.body()?.output?.choices?.getOrNull(0)?.text
                Log.d("AI_JSON", "Extracted JSON: $responseJson")
                if (!responseJson.isNullOrBlank()) {
                    // Cố gắng trích xuất chỉ phần JSON nếu AI trả về thêm text thừa
                    val actualJson = extractJson(responseJson)
                    val aiResponse = gson.fromJson(actualJson, AIQuestionResponse::class.java)
                    if (aiResponse.questions.size == 10) {
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

        val currentQuestions = _testQuestions.value // Assuming this is LiveData<List<TestQuestion>> or similar
        if (currentQuestions == null || currentQuestions.isEmpty()) {
            _errorMesssage.value = "Không có bài test để chấm điểm."
            _isLoading.value = false
            return
        }

        // Prepare user answers string for the prompt (format: question_number,choice;)
        val userAnswersString = userAnswers.joinToString(";") { answer ->
            val questionIndex = currentQuestions.indexOfFirst { it.id == answer.questionId }
            // Use 1-based index for AI prompt if questionId is not reliable for numbering
            if (questionIndex != -1) {
                // Using questionIndex + 1 as the identifier in the prompt (q1, q2, etc.)
                "q${questionIndex + 1},${answer.selectedOptionKey}"
            } else {
                // Fallback to using the question ID if index isn't found (less ideal for AI)
                "${answer.questionId},${answer.selectedOptionKey}"
            }
        } + ";" // Ensure trailing semicolon as requested by the format

        // Prepare questions JSON for grading
        val questionsJsonForGrading = gson.toJson(currentQuestions.map {
            mapOf(
                "id" to it.id,
                "question_text" to it.question_text,
                "options" to it.options,
                "correct_answer" to it.correct_answer // Include correct answer
            )
        })

        val gradingPrompt = """
            Below is a multiple-choice quiz with correct answers and the user's answers.
            Please grade it and return how many answers are correct.
            For each question, provide the status ("correct" or "incorrect").
            If the answer is incorrect, provide a brief "explanation" field detailing why the user's answer is wrong and what the correct answer is, based on the provided question text and options.
            Write explanation in Vietnamese
            Quiz with correct answers (in JSON format):
            ```json
            $questionsJsonForGrading
            ```

            User's answers (format: question_identifier,choice;):
            $userAnswersString

            Return a response in **pure JSON format**, no explanation, no extra text outside the JSON object. The JSON structure should be:
            ```json
            {
              "score": [number_of_correct_answers],
              "total_questions": ${currentQuestions.size}, // Make total_questions dynamic
              "feedback": {
                "q1": {
                  "status": "correct"
                },
                "q2": {
                  "status": "incorrect",
                  "explanation": "Câu trả lời của bạn [user_choice_key] is incorrect. The correct answer is [correct_choice_key] because [brief reason based on question/options]."
                },
                 "q3": {
                  "status": "correct"
                }
                // ... for all questions (q1, q2, q3...)
              }
            }
            ```
        """.trimIndent()

        viewModelScope.launch { // Assume viewModelScope is available
            try {
                val messages = listOf(systemMessageForGrading, Message("user", gradingPrompt))
                // Assume Message and ChatRequest data classes exist
                val request = ChatRequest(
                    model = "deepseek-ai/DeepSeek-R1-Distill-Llama-70B-free", // Or your preferred model
                    messages = messages,
                    temperature = 0.1 // Lower temperature might help for factual grading
                    // Add other parameters if needed, like max_tokens
                )

                // Assume RetrofitClient.instance.chatWithAI() returns Call<ApiResponse>
                // and ApiResponse contains output?.choices?.getOrNull(0)?.text
                val response = RetrofitClient.instance.chatWithAI(request).awaitResponse()
                val responseBody = response.body()

                val responseJson = responseBody?.output?.choices?.getOrNull(0)?.text
                Log.d("AI_GRADING_JSON", "Raw response: $responseJson")

                if (!responseJson.isNullOrBlank()) {
                    // Use the existing extractJson function if needed to clean the response
                    val actualJson = extractJson(responseJson) // Assume extractJson exists
                    Log.d("AI_GRADING_JSON", "Extracted JSON: $actualJson")

                    // Use a try-catch around parsing in case the AI returns malformed JSON
                    try {
                        val result = gson.fromJson(actualJson, AIGradingResult::class.java)
                        _gradingResult.postValue(result)
                    } catch (jsonException: Exception) {
                        Log.e("AI_GRADING_JSON", "JSON Parsing error: ${jsonException.message}", jsonException)
                        _errorMesssage.postValue("Lỗi xử lý kết quả chấm điểm từ AI: ${jsonException.message}")
                    }

                } else {
                    _errorMesssage.postValue("Không nhận được phản hồi chấm điểm từ AI.")
                }

            } catch (e: Exception) {
                Log.e("AI_GRADING", "Lỗi khi gọi API chấm điểm: ${e.message}", e)
                _errorMesssage.postValue("Lỗi khi chấm điểm: ${e.message}")
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
    fun clearGradingResult() {
        _gradingResult.value = null // Setting it to null will hide the result UI
        // Optionally reset error message too if needed
        // _errorMesssage.value = null
    }
    fun clearErrorMessage() {
        _errorMesssage.value = null
    }
}