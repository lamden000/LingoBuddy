package com.example.lingobuddypck.ViewModel

import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lingobuddypck.Factory.PronunciationAiService
import com.example.lingobuddypck.Network.TogetherAI.PronunciationFeedback
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

    fun generateNewReferenceText(topic: String) {
        _isLoading.value = true
        _errorMessage.value = null // Clear previous errors
        updateStatusMessage("Đang tạo câu tham khảo...") // Update status
        viewModelScope.launch {
            try {
                val newText = aiService.generateReferenceText(topic)
                _referenceText.postValue(newText) // Use postValue for background threads
                updateStatusMessage("Sẵn sàng.") // Reset status
            } catch (e: Exception) {
                setErrorMessage("Lỗi khi tạo câu tham khảo: ${e.message}")
                updateStatusMessage("Lỗi.") // Update status on error
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
