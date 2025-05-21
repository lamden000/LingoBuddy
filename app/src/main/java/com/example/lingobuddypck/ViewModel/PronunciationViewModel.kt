package com.example.lingobuddypck.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lingobuddypck.Services.PronunciationAiService
import com.example.lingobuddypck.Services.PronunciationFeedback
import kotlinx.coroutines.launch

class PronunciationViewModel(
    private val aiService: PronunciationAiService
) : ViewModel() {

    private val _referenceText = MutableLiveData<String>()
    val referenceText: LiveData<String> = _referenceText

    private val _userSpeechResult = MutableLiveData<String>()
    val userSpeechResult: LiveData<String> = _userSpeechResult

    private val _pronunciationFeedback = MutableLiveData<PronunciationFeedback?>()
    val pronunciationFeedback: LiveData<PronunciationFeedback?> = _pronunciationFeedback

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        _referenceText.value = "Hello, How are you today?"
        _statusMessage.value = "Sẵn sàng."
    }

    fun updateStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    fun generateNewReferenceText(topic: String, isCustom: Boolean) {
        _isLoading.value = true
        _errorMessage.value = null

        // List of miserable loading messages
        val loadingMessages = listOf(
            "🔄 Đang vò đầu bứt tai để nghĩ ra câu gì đó...",
            "⌛ Xin kiên nhẫn. Tôi đang suy nghĩ chậm rãi như một con ốc buồn.",
            "🤢 Đang lục thùng rác...",
            "🧠 Đang hỏi AI, nó cũng đang than thở.",
            "🗂 Tạm thời chưa tìm được, nhưng tôi có niềm tin... yếu ớt."
        )

        // List of tragically triumphant success messages
        val successMessages = listOf(
            "✔️ Mới tìm thấy câu này trong thùng rác.",
            "🎯 Không dở lắm. Tạm dùng đi.",
            "🤷 Đây là cái tốt nhất tôi tìm được dưới gầm bàn.",
        )

        // Pick one miserable loading message
        updateStatusMessage(loadingMessages.random())

        viewModelScope.launch {
            try {
                val newText = aiService.generateReferenceText(topic, isCustom)

                _referenceText.postValue(newText)

                // Pick one reluctantly proud success message
                updateStatusMessage(successMessages.random())
            } catch (e: Exception) {
                setErrorMessage("💔 Không tìm ra câu tham khảo. Có lẽ AI đã bỏ đi mãi mãi: ${e.message}")
                updateStatusMessage("🪦 Câu tham khảo không qua khỏi.")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun setUserSpeechResult(result: String) {
        _userSpeechResult.value = result
    }

    fun checkPronunciation(userSpeech: String, referenceText: String) {
        _isLoading.value = true
        _errorMessage.value = null // Clear previous errors
        _pronunciationFeedback.value = null // Clear previous feedback
        updateStatusMessage("Đang chấm điểm phát âm...") // Update status
        viewModelScope.launch {
            try {
                val feedback = aiService.checkPronunciation(userSpeech, referenceText)
                _pronunciationFeedback.postValue(feedback)
                updateStatusMessage("Hoàn thành.") // Reset status
            } catch (e: Exception) {
                setErrorMessage("Lỗi chấm điểm phát âm: ${e.message}")
                updateStatusMessage("Lỗi.") // Update status on error
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun clearPronunciationFeedback() {
        _pronunciationFeedback.value = null
    }

    // Factory for ViewModel injection (no changes here)
    class Factory(private val aiService: PronunciationAiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PronunciationViewModel::class.java)) {
                return PronunciationViewModel(aiService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
