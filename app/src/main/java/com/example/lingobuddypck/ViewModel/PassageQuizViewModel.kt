package com.example.lingobuddypck.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lingobuddypck.Services.QuizService.AiQuizService
import com.example.lingobuddypck.Services.AIGradingResult
import com.example.lingobuddypck.Services.PassageQuizData
import com.example.lingobuddypck.Services.QuestionData
import com.example.lingobuddypck.Services.UserAnswer
import com.example.lingobuddypck.Services.QuizService.PassageQuiz.PassageQuizViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PassageQuizViewModelImpl(
    private val aiQuizService: AiQuizService
) : ViewModel(), PassageQuizViewModel {

    private val _isLoading = MutableLiveData<Boolean>()
    override val isLoading: LiveData<Boolean> = _isLoading

    private val _isFetchingPassageTest = MutableLiveData<Boolean>()
    override val isFetchingPassageTest: LiveData<Boolean> = _isFetchingPassageTest

    private val _passageQuizData = MutableLiveData<PassageQuizData?>()
    override val passageQuizData: LiveData<PassageQuizData?> = _passageQuizData

    private val _gradingResult = MutableLiveData<AIGradingResult?>()
    override val gradingResult: LiveData<AIGradingResult?> = _gradingResult

    private val _errorMessage = MutableLiveData<String?>()
    override val errorMessage: LiveData<String?> = _errorMessage

    // Factory for injection
    class Factory(
        private val aiQuizService: AiQuizService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PassageQuizViewModelImpl::class.java)) {
                return PassageQuizViewModelImpl(aiQuizService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    override fun fetchPassageTest(topic: String, isCustom: Boolean) {
        _isLoading.value = true
        _isFetchingPassageTest.value = true
        _passageQuizData.value = null // Clear previous data
        _gradingResult.value = null
        _errorMessage.value = null

        viewModelScope.launch {
            val quizResult = tryFetchPassageQuizOnce(topic, isCustom)
            if (quizResult == null) {
                delay(5000) // Wait 5 seconds before retry
                val retryResult = tryFetchPassageQuizOnce(topic, isCustom)
                if (retryResult == null) {
                    _errorMessage.postValue("Lỗi khi tạo bài đọc điền từ. Vui lòng thử lại sau.")
                } else {
                    _passageQuizData.postValue(retryResult)
                }
            } else {
                _passageQuizData.postValue(quizResult)
            }
            _isLoading.postValue(false)
            _isFetchingPassageTest.postValue(false)
        }
    }

    private suspend fun tryFetchPassageQuizOnce(topic: String, isCustom: Boolean): PassageQuizData? {
        return try {
            aiQuizService.generatePassageQuiz(topic, isCustom)
        } catch (e: Exception) {
            Log.e("PassageQuizViewModel", "Error fetching passage quiz", e)
            null
        }
    }

    override fun submitPassageAnswers(userAnswers: List<UserAnswer>, questions: List<QuestionData>) {
        if (questions.isEmpty()) {
            _errorMessage.value = "Không có câu hỏi để chấm điểm."
            return
        }

        _isLoading.value = true
        _gradingResult.value = null
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val result = aiQuizService.gradeQuiz(questions, userAnswers)
                _gradingResult.postValue(result)
            } catch (e: Exception) {
                Log.e("PassageQuizViewModel", "Error submitting passage answers", e)
                _errorMessage.postValue(e.message)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    override fun clearGradingResult() {
        _gradingResult.value = null
    }

    override fun clearErrorMessage() {
        _errorMessage.value = null
    }

    override fun clearPassageQuizData() {
        _passageQuizData.value = null
    }
}