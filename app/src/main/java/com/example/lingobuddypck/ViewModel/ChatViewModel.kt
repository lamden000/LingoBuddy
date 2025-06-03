package com.example.lingobuddypck.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.Services.ChatRequest
import com.example.lingobuddypck.Services.ChatResponse
import com.example.lingobuddypck.Services.Message
import com.example.lingobuddypck.data.UserProfileBundle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response



class ChatViewModel : ViewModel() {

    private val maxHistorySize = 9
    private val _chatMessages = MutableLiveData<List<Message>>()
    val chatMessages: LiveData<List<Message>> = _chatMessages
    val isLoading = MutableLiveData<Boolean>(false)
    val isWaitingForResponse = MutableLiveData<Boolean>(false)

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val systemMessageContentBase = (
            "Bạn là một trợ lý ảo giúp người học cải thiện khả năng nói tiếng Anh. Tên của bạn là Lingo. " +
                    "QUAN TRỌNG: Nếu người dùng gửi tin nhắn hoàn toàn bằng tiếng Anh, bạn phải phản hồi hoàn toàn bằng tiếng Anh và bọc TOÀN BỘ phản hồi trong cặp thẻ <en>...</en>. " +
                    "Ví dụ: <en>Let's talk about your dogs! What are their names?</en> " +
                    "Nếu người dùng nói bằng tiếng Việt hoặc trộn lẫn hai ngôn ngữ, bạn có thể dùng tiếng Việt để giải thích, nhưng phải bọc toàn bộ các phần tiếng Anh riêng biệt trong thẻ <en>...</en>. " +
                    "Ví dụ: 'Bạn có thể nói: <en>I have two dogs</en> hoặc <en>They are very friendly</en>.' " +
                    "Không bao giờ trộn tiếng Việt và tiếng Anh trong cùng một câu nếu không cần thiết. Không được bỏ sót thẻ <en> nếu có tiếng Anh."
            )
    private var currentSystemMessageContent: String = systemMessageContentBase

    private val fullHistory = mutableListOf<Message>()

    private var fetchedUserPersonalDetails: UserProfileBundle? = null
    private var currentAiTone: String = "trung lập và thân thiện"

    private var initialSessionSetupDone = false

    init {
        initializeChatSessionData()
    }

    private fun initializeChatSessionData() {
        if (initialSessionSetupDone) return
        isLoading.value = true
        isWaitingForResponse.value=false

        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.d("ChatViewModel", "Người dùng chưa đăng nhập. Sử dụng cài đặt mặc định cho chat.")
            this.fetchedUserPersonalDetails = null
            this.currentAiTone = "trung lập và thân thiện"
            rebuildSystemMessageAndFinalizeSetup()
            return
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    Log.d("ChatViewModel", "Tìm thấy document người dùng cho phiên chat.")
                    // Lấy thông tin cá nhân khác
                    fetchedUserPersonalDetails = UserProfileBundle(
                        name = document.getString("name"),
                        job = document.getString("job"),
                        interest = document.getString("interest"),
                        otherInfo = document.getString("otherInfo")
                    )
                    // ⭐ LẤY PHONG CÁCH AI TỪ FIREBASE ⭐
                    currentAiTone = document.getString("aiChatTone") ?: "trung lập và thân thiện"
                    Log.d("ChatViewModel", "Phong cách AI được fetch từ Firebase: $currentAiTone")

                } else {
                    Log.d("ChatViewModel", "Không tìm thấy document người dùng. Sử dụng cài đặt mặc định.")
                    fetchedUserPersonalDetails = null
                    currentAiTone = "trung lập và thân thiện"
                }
                rebuildSystemMessageAndFinalizeSetup()
            }
            .addOnFailureListener { exception ->
                Log.w("ChatViewModel", "Lỗi khi fetch dữ liệu người dùng cho phiên chat", exception)
                fetchedUserPersonalDetails = null
                currentAiTone = "trung lập và thân thiện"
                rebuildSystemMessageAndFinalizeSetup()
            }
    }

    private fun rebuildSystemMessageAndFinalizeSetup() {
        rebuildSystemMessageInternal() // Gọi hàm nội bộ để xây dựng system message
        if (!initialSessionSetupDone) {
            setupInitialMessages() // Chỉ setup tin nhắn chào mừng lần đầu
            initialSessionSetupDone = true
        }
        isLoading.value = false // Kết thúc trạng thái loading ban đầu
    }

    private fun rebuildSystemMessageInternal() {
        var newSystemContent = systemMessageContentBase

        // Thêm thông tin cá nhân của người dùng (nếu có)
        fetchedUserPersonalDetails?.let { details ->
            val userInfoParts = mutableListOf<String>()
            if (!details.name.isNullOrBlank()) userInfoParts.add("- Tên người dùng: ${details.name}")
            if (!details.job.isNullOrBlank()) userInfoParts.add("- Công việc: ${details.job}")
            if (!details.interest.isNullOrBlank()) userInfoParts.add("- Sở thích: ${details.interest}")
            if (!details.otherInfo.isNullOrBlank()) userInfoParts.add("- Thông tin thêm: ${details.otherInfo}")

            if (userInfoParts.isNotEmpty()) {
                newSystemContent += "\n\nThông tin về người học:\n" + userInfoParts.joinToString("\n")
            }
        }

        if (currentAiTone.isNotBlank()) {
            newSystemContent += "\n\nHãy cố gắng trò chuyện với phong cách sau: $currentAiTone."
        } else {
            newSystemContent += "\n\nHãy cố gắng trò chuyện với phong cách: trung lập và thân thiện."
        }

        currentSystemMessageContent = newSystemContent
        Log.d("ChatViewModel", "System message đã được xây dựng: $currentSystemMessageContent")
    }

    private fun setupInitialMessages() {
        fullHistory.clear()
        val assistantWelcomeMessage = Message("assistant", "Xin chào! Tôi là Lingo. Chúng ta cùng bắt đầu buổi học tiếng Anh nhé?")
        fullHistory.add(assistantWelcomeMessage)
        _chatMessages.value = fullHistory.toList()
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return

        val userMessage = Message("user", userInput)
        fullHistory.add(userMessage)
        _chatMessages.value = fullHistory.toList()
        isWaitingForResponse.value=true

        val historyForAI = getHistoryForAI()
        Log.d("ChatViewModel", "Gửi tới AI với system message: ${historyForAI.firstOrNull { it.role == "system" }?.content}")

        val request = ChatRequest(
            model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
            messages = historyForAI
        )

        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                isLoading.postValue(false)
                val aiResponseText = response.body()?.output?.choices?.getOrNull(0)?.text
                if (response.isSuccessful && !aiResponseText.isNullOrEmpty()) {
                    val assistantMessage = Message("assistant", aiResponseText)
                    Log.d("DEBUG_CL",aiResponseText)
                    fullHistory.add(assistantMessage)
                    _chatMessages.postValue(fullHistory.toList())
                    isWaitingForResponse.value=false
                } else {
                    Log.e("ChatViewModel", "AI response không thành công hoặc trống. Code: ${response.code()}")
                    val errorMessage = Message("assistant", "Xin lỗi, tôi đang gặp chút vấn đề. Bạn thử lại sau nhé.")
                    fullHistory.add(errorMessage)
                    _chatMessages.postValue(fullHistory.toList())
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                isLoading.postValue(false)
                Log.e("ChatViewModel", "AI Chat API call failed", t)
                val errorMessage = Message("assistant", "Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng và thử lại.")
                fullHistory.add(errorMessage)
                _chatMessages.postValue(fullHistory.toList())
            }
        })
    }

    private fun getHistoryForAI(): List<Message> {
        val conversationTurns = mutableListOf<Message>()
        conversationTurns.addAll(fullHistory)
        while (conversationTurns.size > maxHistorySize) {
            conversationTurns.removeAt(0)
        }
        // Luôn tạo mới Message object cho system để đảm bảo nội dung là mới nhất
        val systemMsg = Message("system", currentSystemMessageContent)
        return listOf(systemMsg) + conversationTurns
    }
}