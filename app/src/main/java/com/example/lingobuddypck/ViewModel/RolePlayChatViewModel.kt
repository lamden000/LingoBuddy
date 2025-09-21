package com.example.lingobuddypck.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.Services.ChatRequest
import com.example.lingobuddypck.Services.ChatResponse
import com.example.lingobuddypck.Services.Message
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RolePlayChatViewModel(
    private val userRole: String,
    private val aiRole: String,
    private val context: String
) : ViewModel() {

    private val maxHistorySize = 5
    private var welcome:Message = Message("system", "Chúng ta sẽ bắt đầu vai trò: Tôi: $aiRole - Bạn: $userRole - Bối cảnh: $context. Bạn sẵn sàng chưa?")

    private val _chatMessages = MutableLiveData<List<Message>>()
    val chatMessages: LiveData<List<Message>> = _chatMessages
    val isWaitingForResponse = MutableLiveData<Boolean>(false)
    val isLoading = MutableLiveData<Boolean>()

    private val systemMessage = Message(
        role = "system",
        content = buildSystemPrompt(aiRole, userRole, context)
    )

    private val fullHistory = mutableListOf(systemMessage)

    init {
        fullHistory.add(welcome)
        _chatMessages.value = fullHistory.filter { it != systemMessage}
        isWaitingForResponse.value=false
    }

    fun sendMessage(userInput: String) {
        val userMessage = Message("user", userInput)
        fullHistory.add(userMessage)
        _chatMessages.value = fullHistory.filter { it != systemMessage }
        isWaitingForResponse.value = true

        val recentHistory = getRecentHistory()
        val request = ChatRequest(
            model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
            messages = recentHistory,
        )

        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                isLoading.postValue(false)
                val aiResponse = response.body()?.output?.choices?.getOrNull(0)?.text
                val output = aiResponse?.replace(
                    Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL),
                    ""
                )
                if (!aiResponse.isNullOrEmpty()) {
                    val assistantMessage = Message("assistant", output)
                    fullHistory.add(assistantMessage)
                    isWaitingForResponse.value = false
                    _chatMessages.postValue(fullHistory.filter { it != systemMessage })
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {

            }
        })
    }

    private fun getRecentHistory(): List<Message> {
        val recent = if (fullHistory.size > maxHistorySize) {
            fullHistory.takeLast(maxHistorySize)
        } else {
            fullHistory
        }
        return listOf(systemMessage)+recent.filter { it != systemMessage &&  it != welcome}
    }

    private fun buildSystemPrompt(aiRole: String, userRole: String, context: String): String {
        return "INSTRUCTION: "+"""
        You are role-playing as "$aiRole" in the context of "$context".  
        I am playing the role of "$userRole".

        Respond entirely in natural, fluent English, as if you were a real person in character.

        Encourage me to continue the conversation by asking follow-up questions or adding more relevant context.

        IMPORTANT:
        - If my English contains any mistakes (grammar, vocabulary, word choice, tone...), include a [Sửa Lỗi] section at the end of your message.
        - The [Sửa Lỗi] section must be written in Vietnamese.
        - In that section, do the following:
          + Point out the errors in my English.
          + Provide the corrected English version.
          + Briefly explain the correction in Vietnamese.

        Notes:
        - All English in your own response must be wrapped with `<en>` and `</en>` tags so I can choose the right speaking accent.
        - Example: 'Câu bạn nói <en>You will have to order food from restaurant nearby</en> là sai vì [explanation in Vietnamese]. Bạn nên nói <en>You will have to order food from a nearby restaurant</en>.'

        Your response format should be:
        <en>[Your English response goes here]</en>

        [Sửa Lỗi]  
        [Your corrections for my English written in Vietnamese]
        For each sentence, make sure every English word or phrase is properly wrapped in <en> and </en> tags. If you forget, it will be considered a mistake.
    """.trimIndent()
    }
}
