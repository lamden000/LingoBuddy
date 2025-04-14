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

class ChatViewModel : ViewModel() {

    private val maxHistorySize = 10
    private val _chatMessages = MutableLiveData<List<Message>>()
    val chatMessages: LiveData<List<Message>> = _chatMessages

    private val systemMessage = Message(
        "system",
        "Bạn là một trợ lý ảo giúp người học cải thiện tiếng Anh. Nếu người dùng nói tiếng Việt cung cấp phản hồi (Tiếng Việt-Tiếng Anh), Tên:Lingo"
    )

    private val fullHistory = mutableListOf<Message>(
        systemMessage
    )

    init {
        val welcome = Message("system", "Tôi giúp gì được cho bạn hôm nay?")
        fullHistory.add(welcome)

        // UI only sees messages excluding the hidden system prompt
        _chatMessages.value = fullHistory.filter { it != systemMessage }
    }
    fun sendMessage(userInput: String) {
        val userMessage = Message("user", userInput)
        fullHistory.add(userMessage)
        _chatMessages.value = fullHistory.filter { it != systemMessage }

        val recentHistory = getRecentHistory()
        val request = ChatRequest(
            model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
            messages = recentHistory
        )

        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                val aiResponse = response.body()?.output?.choices?.getOrNull(0)?.text
                if (!aiResponse.isNullOrEmpty()) {
                    val assistantMessage = Message("assistant", aiResponse)
                    fullHistory.add(assistantMessage)
                    _chatMessages.postValue(fullHistory.filter { it != systemMessage })
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                // You can post a system error message here if desired
            }
        })
    }

    private fun getRecentHistory(): List<Message> {
        return if (fullHistory.size > maxHistorySize) {
            fullHistory.takeLast(maxHistorySize)
        } else {
            fullHistory
        }
    }
}