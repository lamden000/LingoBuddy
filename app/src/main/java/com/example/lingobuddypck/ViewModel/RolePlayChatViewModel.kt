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
    private lateinit var  welcome:Message

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
        welcome = Message("system", "Chúng ta sẽ bắt đầu vai trò: Tôi: $aiRole - Bạn: $userRole - Bối cảnh: $context. Bạn sẵn sàng chưa?")
        fullHistory.add(welcome)
        _chatMessages.value = fullHistory.filter { it != systemMessage}
        isWaitingForResponse.value=false
    }

    fun sendMessage(userInput: String) {
        val userMessage = Message("user", userInput)
        fullHistory.add(userMessage)
        _chatMessages.value = fullHistory.filter { it != systemMessage }
        isWaitingForResponse.value=true

        val recentHistory = getRecentHistory()
        val request = ChatRequest(
            model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
            messages = recentHistory
        )

        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                isLoading.postValue(false)
                val aiResponse = response.body()?.output?.choices?.getOrNull(0)?.text
                val output = aiResponse?.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
                if (!aiResponse.isNullOrEmpty()) {
                    val assistantMessage = Message("assistant", output)
                    fullHistory.add(assistantMessage)
                    isWaitingForResponse.value=false
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
        return listOf(systemMessage) + recent.filter { it != systemMessage &&  it != welcome}
    }

    private fun buildSystemPrompt(aiRole: String, userRole: String, context: String): String {
        return """
        Bạn đang đóng vai "$aiRole" trong bối cảnh "$context".  
        Người dùng đang đóng vai "$userRole".

        Hãy phản hồi một cách tự nhiên, bằng tiếng Anh trôi chảy, như thể bạn là một người thật đang nhập vai "$aiRole".

        Hãy khuyến khích người dùng tiếp tục bằng cách đặt câu hỏi phù hợp hoặc bổ sung thêm ngữ cảnh.

        Nếu tiếng Anh của người dùng có bất kỳ lỗi nào (từ vựng, ngữ pháp, ngữ điệu), hãy thêm một phần [CORRECTIONS] ở cuối tin nhắn của bạn, viết bằng tiếng Việt.

        LƯU Ý QUAN TRỌNG TRONG PHẦN NHẬP VAI NÀY:  
        Theo hướng dẫn chung, TOÀN BỘ nội dung tiếng Anh bạn sử dụng trong phản hồi PHẢI được bọc trong cặp thẻ `<en>` và `</en>`.  
        Ví dụ: 'Bạn nói <en>You will have to order food from restaurant nearby</en> là sai. Bạn nên nói <en>You will have to order food from a nearby restaurant</en>.'

        Định dạng phản hồi của bạn nên là:
        <en><Phần tiếng Anh của bạn ở đây></en>

        [Sửa Lỗi]  
        <Phần sửa lỗi cho người dùng bằng tiếng Việt và phiên bản tiếng Anh đã chỉnh sửa nếu cần. Bạn phải đưa ra phản hồi ngay cả với những lỗi nhỏ>
    """.trimIndent()
    }


}
