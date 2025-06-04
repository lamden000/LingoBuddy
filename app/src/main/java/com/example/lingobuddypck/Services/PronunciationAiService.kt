package com.example.lingobuddypck.Services

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse

class PronunciationAiService(
    private val retrofitClient: TogetherApi,
    private val gson: Gson
) {

    private fun removeThinkTags(text: String): String {
        // Regex to find <think>...</think> including content, non-greedy
        val regex = "<think>[\\s\\S]*?<\\/think>".toRegex()
        return text.replace(regex, "").trim() // Replace found matches with empty string
    }

    suspend fun generateReferenceText(topic: String,isCustom:Boolean): String {
        val prompt = """
            Generate a concise English sentence (around 10-15 words) suitable for pronunciation practice. The sentence should be related to "$topic".
            Ensure the sentence is natural and grammatically correct.
            Return only the sentence, nothing else.
            Example: "The quick brown fox jumps over the lazy dog."
            Topic: "$topic"
            Sentence:
        """.trimIndent()

        val model = if (isCustom) "deepseek-ai/DeepSeek-R1-Distill-Llama-70B-free" else "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free"

        val request = ChatRequest(
            model,temperature=1.3, messages = listOf(
                Message("user", prompt)
            )
        )
        val response = retrofitClient.chatWithAI(request).awaitResponse()

        return if (response.isSuccessful) {
            val rawText = response.body()?.output?.choices?.get(0)?.text
            if (rawText != null) {
                removeThinkTags(rawText).trim() // Apply the filter here
                    ?: "Failed to generate reference text. Please try again."
            } else {
                "Failed to generate reference text: Empty response."
            }
        } else {
            "Error generating reference text: ${response.code()} - ${response.errorBody()?.string()}"
        }
    }

    // Checks pronunciation and returns structured feedback
    suspend fun checkPronunciation(userSpeech: String, referenceText: String): PronunciationFeedback {
        val prompt = """
            I will provide an English sentence and a user's spoken version of that sentence.
            Please grade the pronunciation on a scale of 0-10 and identify any mistakes.
            Then, suggest improvements for the mistakes (ignore punctuation as this is about pronunciation).

            Reference sentence: "$referenceText"
            User's spoken sentence: "$userSpeech"

            Return the response in **pure JSON format only**, no extra text outside the JSON object. The JSON structure should be:
            ```json
            {
              "score": 8.5,
              "mistakes": ["word1", "word2"],
              "suggestions": ["Phát âm lại 'word1' với âm /æ/ thay vì /e/", "Lưu ý trọng âm của 'word2'."]
            }
            ```
        """.trimIndent()

        val request = ChatRequest(model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free", messages = listOf(
            Message("user", prompt)
        ), temperature = 0.7)

        val response = withContext(Dispatchers.IO) {
            retrofitClient.chatWithAI(request).awaitResponse()
        }

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            throw Exception("API call for pronunciation check failed with code ${response.code()}: $errorBody")
        }

        val responseBodyText = response.body()?.output?.choices?.getOrNull(0)?.text
        if (responseBodyText.isNullOrBlank()) {
            throw Exception("No response from AI for pronunciation check.")
        }

        val cleanedResponseText = removeThinkTags(responseBodyText)
        // ---------------------------------------------

        val actualJson = extractJson(cleanedResponseText) // extractJson will then handle ```json blocks

        return try {
            gson.fromJson(actualJson, PronunciationFeedback::class.java)
        } catch (e: Exception) {
            throw Exception("Error parsing pronunciation feedback JSON: ${e.message}. Raw: $actualJson", e)
        }
    }

    private fun extractJson(responseText: String): String {
        val jsonPattern = "```json\\s*([\\s\\S]*?)\\s*```".toRegex()
        val match = jsonPattern.find(responseText)
        // This function will now work on text that *already* had <think> tags removed
        return match?.groups?.get(1)?.value?.trim() ?: responseText.trim()
    }
}