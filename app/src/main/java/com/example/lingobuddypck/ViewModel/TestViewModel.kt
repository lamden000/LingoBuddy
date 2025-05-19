package com.example.lingobuddypck.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lingobuddypck.Network.TogetherAI.AIGradingResult
import com.example.lingobuddypck.Network.TogetherAI.QuestionData
import com.example.lingobuddypck.Network.TogetherAI.UserAnswer
import kotlinx.coroutines.launch

import androidx.lifecycle.ViewModelProvider
import com.example.lingobuddypck.Factory.QuizService.AiQuizService
import com.example.lingobuddypck.Factory.QuizService.QuizViewModel


class TestViewModel(
    private val aiQuizService: AiQuizService
) : ViewModel(), QuizViewModel {

    // LiveData remain to hold the UI state
    private val _isLoading = MutableLiveData<Boolean>()
    override val isLoading: LiveData<Boolean> = _isLoading

    private val _isFetchingTest = MutableLiveData<Boolean>()
    override val isFetchingTest: LiveData<Boolean> = _isFetchingTest

    private val _testQuestions = MutableLiveData<List<QuestionData>?>()
    override val testQuestions: LiveData<List<QuestionData>?> = _testQuestions

    private val _gradingResult = MutableLiveData<AIGradingResult?>()
    override val gradingResult: LiveData<AIGradingResult?> = _gradingResult

    private val _errorMesssage = MutableLiveData<String?>()
    override val errorMessage: LiveData<String?> = _errorMesssage
    class Factory(
        private val aiQuizService: AiQuizService
        // Add other dependencies needed by TestViewModel constructor here
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TestViewModel::class.java)) {
                return TestViewModel(aiQuizService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

   override fun fetchTest(topic: String, isCustom: Boolean) {
        // ViewModel updates its own loading state
        _isLoading.value = true
        _testQuestions.value = null // Clear previous state
        _gradingResult.value = null
        _errorMesssage.value = null
       _isFetchingTest.value=true

        viewModelScope.launch {
            try {
                val questions = aiQuizService.generateQuiz(topic, isCustom)
                _testQuestions.postValue(questions)
            } catch (e: Exception) {
                // Handle exceptions thrown by the service
                Log.e("TestViewModel", "Error fetching test", e) // Log the error
                _errorMesssage.postValue(e.message)
            } finally {
                // ViewModel updates its own loading state
                _isLoading.postValue(false)
                _isFetchingTest.postValue(false)
            }
        }
    }

    override fun submitAnswers(userAnswers: List<UserAnswer>) {
        val currentQuestions = _testQuestions.value
        if (currentQuestions == null || currentQuestions.isEmpty()) {
            _errorMesssage.value = "Không có bài test để chấm điểm."
            return
        }

        // ViewModel updates its own loading state
        _isLoading.value = true
        _gradingResult.value = null
        _errorMesssage.value = null

        viewModelScope.launch {
            try {
                // Call the service function
                val result = aiQuizService.gradeQuiz(currentQuestions, userAnswers)
                // Update ViewModel LiveData with the result
                _gradingResult.postValue(result)
            } catch (e: Exception) {
                // Handle exceptions thrown by the service
                Log.e("TestViewModel", "Error submitting answers", e) // Log the error
                _errorMesssage.postValue(e.message)
            } finally {
                // ViewModel updates its own loading state
                _isLoading.postValue(false)
            }
        }
    }

    // These remain as they manage ViewModel-specific state
    override fun clearGradingResult() {
        _gradingResult.value = null
    }

    override fun clearErrorMessage() {
        _errorMesssage.value = null
    }

    override fun clearQuestions() {
        _testQuestions.value= null
    }
}