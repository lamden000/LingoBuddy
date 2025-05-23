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

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val systemMessageContentBase = "Bạn là một trợ lý ảo giúp người học cải thiện tiếng Anh, hãy dạy người dùng tiếng anh một cách thân thiện và hiệu quả. Tên của bạn là Lingo."
    private var currentSystemMessageContent: String = systemMessageContentBase

    private val fullHistory = mutableListOf<Message>()

    // Biến để lưu thông tin cá nhân khác (nếu có)
    private var fetchedUserPersonalDetails: UserProfileBundle? = null
    // Biến để lưu phong cách AI được fetch từ Firebase
    private var currentAiTone: String = "trung lập và thân thiện" // Giá trị mặc định

    private var initialSessionSetupDone = false

    init {
        // Khi ViewModel được tạo, bắt đầu quá trình lấy dữ liệu cho session chat
        initializeChatSessionData()
    }

    private fun initializeChatSessionData() {
        if (initialSessionSetupDone) return
        isLoading.value = true

        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.d("ChatViewModel", "Người dùng chưa đăng nhập. Sử dụng cài đặt mặc định cho chat.")
            this.fetchedUserPersonalDetails = null // Reset nếu user logout rồi login lại
            this.currentAiTone = "trung lập và thân thiện" // Reset về mặc định
            rebuildSystemMessageAndFinalizeSetup() // Xây dựng system message và hoàn tất setup
            return
        }

        // Fetch document người dùng từ Firestore
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
                    currentAiTone = "trung lập và thân thiện" // Mặc định nếu không có dữ liệu
                }
                rebuildSystemMessageAndFinalizeSetup() // Xây dựng system message và hoàn tất setup
            }
            .addOnFailureListener { exception ->
                Log.w("ChatViewModel", "Lỗi khi fetch dữ liệu người dùng cho phiên chat", exception)
                fetchedUserPersonalDetails = null
                currentAiTone = "trung lập và thân thiện" // Mặc định khi có lỗi
                rebuildSystemMessageAndFinalizeSetup()
            }
    }

    /**
     * Xây dựng lại nội dung system message hoàn chỉnh và hoàn tất các bước setup ban đầu.
     */
    private fun rebuildSystemMessageAndFinalizeSetup() {
        rebuildSystemMessageInternal() // Gọi hàm nội bộ để xây dựng system message
        if (!initialSessionSetupDone) {
            setupInitialMessages() // Chỉ setup tin nhắn chào mừng lần đầu
            initialSessionSetupDone = true
        }
        isLoading.value = false // Kết thúc trạng thái loading ban đầu
    }

    /**
     * Hàm nội bộ để xây dựng lại currentSystemMessageContent.
     * Sử dụng thông tin cá nhân đã fetch (fetchedUserPersonalDetails)
     * và phong cách AI hiện tại (currentAiTone).
     */
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

        // ⭐ SỬ DỤNG PHONG CÁCH AI ĐÃ FETCH ĐỂ THÊM VÀO SYSTEM MESSAGE ⭐
        if (currentAiTone.isNotBlank()) {
            newSystemContent += "\n\nHãy cố gắng trò chuyện với phong cách sau: $currentAiTone."
        } else {
            // Nếu currentAiTone trống (ví dụ người dùng xóa trắng và lưu), có thể dùng một phong cách mặc định khác ở đây
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

    // Hàm sendMessage và getHistoryForAI không thay đổi,
    // vì getHistoryForAI đã sử dụng `currentSystemMessageContent` được cập nhật.
    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return

        val userMessage = Message("user", userInput)
        fullHistory.add(userMessage)
        _chatMessages.value = fullHistory.toList()
        isLoading.value = true // Loading cho phản hồi của AI

        val historyForAI = getHistoryForAI()
        Log.d("ChatViewModel", "Gửi tới AI với system message: ${historyForAI.firstOrNull { it.role == "system" }?.content}")

        val request = ChatRequest(
            model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free", // Hoặc model của bạn
            messages = historyForAI
        )

        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                isLoading.postValue(false)
                val aiResponseText = response.body()?.output?.choices?.getOrNull(0)?.text
                if (response.isSuccessful && !aiResponseText.isNullOrEmpty()) {
                    val assistantMessage = Message("assistant", aiResponseText)
                    fullHistory.add(assistantMessage)
                    _chatMessages.postValue(fullHistory.toList())
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