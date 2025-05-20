package com.example.lingobuddypck.Factory.QuizService
import android.content.Context
import com.example.lingobuddypck.Network.TogetherAI.AIGradingResult
import com.example.lingobuddypck.Network.TogetherAI.AIQuestionResponse
import com.example.lingobuddypck.Network.TogetherAI.ChatRequest
import com.example.lingobuddypck.Network.TogetherAI.Message
import com.example.lingobuddypck.Network.TogetherAI.PassageQuizData
import com.example.lingobuddypck.Network.TogetherAI.QuestionData
import com.example.lingobuddypck.Network.TogetherAI.TogetherApi
import com.example.lingobuddypck.Network.TogetherAI.UserAnswer
import com.google.gson.Gson
import retrofit2.awaitResponse
import java.io.IOException

class AiQuizService(
    private val gson: Gson,
    private val retrofitClient: TogetherApi
) {

    // System messages (can be internal to the service)
    private val systemMessageForTestGeneration = Message(
        "system",
        "Bạn là một trợ lý AI chuyên tạo bài kiểm tra tiếng Anh."
    )
    private val systemMessageForGrading = Message(
        "system",
        "Bạn là một trợ lý AI chuyên chấm điểm bài kiểm tra tiếng Anh."
    )

    // Helper function to extract JSON (keep it inside the service)
    private fun extractJson(rawResponse: String): String {
        val jsonStartIndex = rawResponse.indexOfFirst { it == '{' || it == '[' }
        val jsonEndIndex = rawResponse.indexOfLast { it == '}' || it == ']' }

        return if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex > jsonStartIndex) {
            rawResponse.substring(jsonStartIndex, jsonEndIndex + 1)
        } else {
            rawResponse // Or handle error appropriately
        }
    }

    /**
     * Generates a multiple-choice English quiz using AI.
     * @param topic The topic of the quiz.
     * @param isCustom Whether the topic is custom or from a predefined list (might affect model choice).
     * @return A list of generated questions.
     * @throws Exception if there's an API error, network issue, or unexpected response.
     */
    suspend fun generateQuiz(topic: String, isCustom: Boolean): List<QuestionData> {
        val randomType = listOf("Grammar", "Vocabulary")
        val type = randomType.random()

        val prompt = """
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
            - The correct answers are randomly distributed into a,b,c,d

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
            ```
        """.trimIndent()

        val messages = listOf(systemMessageForTestGeneration, Message("user", prompt))
        val model = if (isCustom) "deepseek-ai/DeepSeek-R1-Distill-Llama-70B-free" else "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free" // Model selection logic here
        val request = ChatRequest(model, messages = messages) // Assuming ChatRequest is a data class

        val response = retrofitClient.chatWithAI(request).awaitResponse() // Assuming chatWithAI is in RetrofitClient

        if (!response.isSuccessful) {
            throw Exception("API call failed with code ${response.code()}")
        }

        val responseBody = response.body()
        val responseJson = responseBody?.output?.choices?.getOrNull(0)?.text

        if (!responseJson.isNullOrBlank()) {
            val actualJson = extractJson(responseJson)
            try {
                val aiResponse = gson.fromJson(actualJson, AIQuestionResponse::class.java) // Assuming AIQuestionResponse has a List<QuestionData> field named 'questions'
                if (aiResponse.questions.size == 10) { // Check the expected number of questions
                    return aiResponse.questions
                } else {
                    throw Exception("AI không trả về đủ 10 câu hỏi.")
                }
            } catch (jsonException: Exception) {
                throw Exception("Lỗi xử lý phản hồi JSON khi tạo bài test: ${jsonException.message}", jsonException)
            }
        } else {
            throw Exception("Không nhận được phản hồi từ AI hoặc phản hồi rỗng khi tạo bài test.")
        }
    }

    suspend fun generatePassageQuiz(topic: String, isCustom: Boolean): PassageQuizData {
        val prompt = """
        Please generate one English language learning passage (around 150–200 words) on the topic "$topic".

        The passage should have **at least 5 blanks**, each blank representing a missing word or phrase.
        The missing parts should be **diverse**, including grammar points, vocabulary, and idiomatic expressions.
        Each blank in the passage should be clearly marked with a numbered placeholder like (1), (2), (3), etc.

        For each blank, provide:
        - An "id" corresponding to the blank number (e.g., "blank1" for blank (1))
        - Four multiple-choice options labeled a, b, c, d
        - The correct answer key (a, b, c, or d)

        Make sure:
        - All options are plausible and grammatically valid, except only one is contextually correct.
        - The correct answers are **accurate and randomized among a/b/c/d**
        - The passage should still be understandable even with the blanks.

        Return the result in **pure JSON format only**, structured as below:
        ```json
        {
          "passage": "Full passage text here, with numbered blanks like (1), (2), (3), etc. For example: 'The cat (1)___ on the mat. It was (2)___ very softly.'",
          "questions": [
            {
              "id": "blank1",
              "options": {
                "a": "slept",
                "b": "sleeps",
                "c": "sleeping",
                "d": "has slept"
              },
              "correct_answer": "a"
            },
            {
              "id": "blank2",
              "options": { "a": "purring", "b": "purrs", "c": "purred", "d": "to purr" },
              "correct_answer": "a"
            },
            // at least 5 items
          ]
        }
        ```
    """.trimIndent()

        val messages = listOf(systemMessageForTestGeneration, Message("user", prompt))
        val model = if (isCustom) "deepseek-ai/DeepSeek-R1-Distill-Llama-70B-free" else "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free"
        val request = ChatRequest(model, messages = messages)

        val response = retrofitClient.chatWithAI(request).awaitResponse()

        if (!response.isSuccessful) {
            throw Exception("API call failed with code ${response.code()}")
        }

        val responseBody = response.body()
        val responseJson = responseBody?.output?.choices?.getOrNull(0)?.text

        if (!responseJson.isNullOrBlank()) {
            val actualJson = extractJson(responseJson)
            try {
                val aiResponse = gson.fromJson(actualJson, PassageQuizData::class.java)
                if (aiResponse.questions.size >= 5) {
                    return aiResponse
                } else {
                    throw Exception("AI không trả về đủ số câu hỏi cần thiết trong passage.")
                }
            } catch (jsonException: Exception) {
                throw Exception("Lỗi xử lý JSON trong bài passage: ${jsonException.message}", jsonException)
            }
        } else {
            throw Exception("Không nhận được phản hồi hoặc phản hồi rỗng khi tạo passage quiz.")
        }
    }


    suspend fun gradeQuiz(questions: List<QuestionData>, userAnswers: List<UserAnswer>): AIGradingResult {
        if (questions.isEmpty()) {
            throw Exception("Không có câu hỏi để chấm điểm.")
        }

        // Prepare user answers string (same logic as before)
        val userAnswersString = userAnswers.joinToString(";") { answer ->
            val questionIndex = questions.indexOfFirst { it.id == answer.questionId }
            if (questionIndex != -1) {
                "q${questionIndex + 1},${answer.selectedOptionKey}"
            } else {
                "${answer.questionId},${answer.selectedOptionKey}" // Fallback
            }
        } + ";"

        // Prepare questions JSON for grading (same logic as before)
        val questionsJsonForGrading = gson.toJson(questions.map {
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
            For each question, provide the status ("correct" or "incorrect").
            FOR EVERY INCORRECT ANSWER, YOU MUST PROVIDE A DETAILED "explanation" FIELD IN VIETNAMESE.
            The explanation should clearly state why the user's chosen answer is wrong and what the correct answer is, based on the provided question text and options.
        
            Quiz with correct answers (in JSON format):
            ```json
            $questionsJsonForGrading
            ```
        
            User's answers (format: question_identifier,choice;):
            $userAnswersString
        
            Return a response in **pure JSON format**, no explanation, no extra text outside the JSON object. The JSON structure MUST use the **exact 'id' values from the provided quiz questions (e.g., "q1", "blank1", etc.) as keys** in the "feedback" object:
            ```json
            {
              "score": [number_of_correct_answers],
              "total_questions": ${questions.size},
              "feedback": {
                "id_of_question_1": { 
                  "status": "correct"
                },
                "id_of_question_2": { 
                  "status": "incorrect",
                  "explanation": "Câu trả lời của bạn [user_choice_key] sai. Đáp án đúng là [correct_choice_key] bởi vì..."
                },
                // ... for all questions, using their original IDs (e.g., "blank1", "q1", etc.)
              }
            }
            ```
        """.trimIndent()

        val messages = listOf(systemMessageForGrading, Message("user", gradingPrompt))
        val request = ChatRequest(
            model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free", // Or parameterize the model
            messages = messages,
            temperature = 0.1
        )

        val response = retrofitClient.chatWithAI(request).awaitResponse()

        if (!response.isSuccessful) {
            throw Exception("API call failed with code ${response.code()}")
        }

        val responseBody = response.body()
        val responseJson = responseBody?.output?.choices?.getOrNull(0)?.text

        if (!responseJson.isNullOrBlank()) {
            val actualJson = extractJson(responseJson)
            try {
                val result = gson.fromJson(actualJson, AIGradingResult::class.java)
                // You might add checks here, e.g., if result is null or score/total seems off
                return result
            } catch (jsonException: Exception) {
                throw Exception("Lỗi xử lý kết quả chấm điểm JSON từ AI: ${jsonException.message}", jsonException)
            }
        } else {
            throw Exception("Không nhận được phản hồi chấm điểm từ AI hoặc phản hồi rỗng.")
        }
    }
}

