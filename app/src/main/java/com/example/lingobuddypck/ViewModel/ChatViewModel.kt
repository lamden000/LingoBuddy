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

    val isLoading = MutableLiveData<Boolean>()

    private val systemMessage = Message(
        "system",
        "Bạn là một trợ lý ảo giúp người học cải thiện tiếng Anh, Tên:Lingo. Nếu nguời dùng nói tiếng anh, giúp họ sửa lỗi ngữ pháp hoặc từ vựng (nếu có, giải thích bằng tiếng Việt) "
    )

    private val fullHistory = mutableListOf(systemMessage)

    init {
        val welcome = Message("system", "Tôi giúp gì được cho bạn hôm nay?")
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
                // Có thể thêm xử lý lỗi tại đây
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
