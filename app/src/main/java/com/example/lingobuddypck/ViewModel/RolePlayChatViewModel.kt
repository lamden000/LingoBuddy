package com.example.lingobuddypck.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.Network.TogetherAI.ChatRequest
import com.example.lingobuddypck.Network.TogetherAI.ChatResponse
import com.example.lingobuddypck.Network.TogetherAI.Message
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RolePlayChatViewModel(
    private val userRole: String,
    private val aiRole: String,
    private val context: String
) : ViewModel() {

    private val maxHistorySize = 5

    private val _chatMessages = MutableLiveData<List<Message>>()
    val chatMessages: LiveData<List<Message>> = _chatMessages

    val isLoading = MutableLiveData<Boolean>()

    private val systemMessage = Message(
        role = "system",
        content = buildSystemPrompt(aiRole, userRole, context)
    )

    private val fullHistory = mutableListOf(systemMessage)

    init {
        val welcome = Message("system", "Chúng ta sẽ bắt đầu vai trò: tôi: $aiRole - $context - bạn: $userRole. Bạn sẵn sàng chưa?")
        fullHistory.add(welcome)
        _chatMessages.value = fullHistory.filter { it != systemMessage }
    }

    fun sendMessage(userInput: String) {
        val userMessage = Message("user", userInput)
        fullHistory.add(userMessage)
        _chatMessages.value = fullHistory.filter { it != systemMessage }

        isLoading.postValue(true)

        val recentHistory = getRecentHistory()
        val request = ChatRequest(
            model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
            messages = recentHistory
        )

        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                isLoading.postValue(false)
                val aiResponse = response.body()?.output?.choices?.getOrNull(0)?.text
                if (!aiResponse.isNullOrEmpty()) {
                    val assistantMessage = Message("assistant", aiResponse)
                    fullHistory.add(assistantMessage)
                    _chatMessages.postValue(fullHistory.filter { it != systemMessage })
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                isLoading.postValue(false)
                // Optional: handle error
            }
        })
    }

    private fun getRecentHistory(): List<Message> {
        val recent = if (fullHistory.size > maxHistorySize) {
            fullHistory.takeLast(maxHistorySize)
        } else {
            fullHistory
        }
        return listOf(systemMessage) + recent.filter { it != systemMessage }
    }

    private fun buildSystemPrompt(aiRole: String,userRole:String, context: String): String {
        return "You are playing the role of \"$aiRole\" in the context of \"$context\". The user is acting as \"$userRole\".\n" +
                "\n" +
                "Respond naturally, in fluent English, as if you are a real person playing \"$aiRole\".\n" +
                "\n" +
                "Encourage the user to continue by adding relevant questions or context.\n" +
                "\n" +
                "If the user's English has any errors, provide corrections at the end.\n" +
                "\n" +
                "Format your response as:\n" +
                "1. English reply (in character)\n" +
                "2. Corrections (in Vietnamese, if any)"
    }
}
