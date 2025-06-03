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
import com.example.lingobuddypck.data.DisplayableQuizContent
import com.example.lingobuddypck.data.QuizDisplayType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class PassageQuizViewModelImpl(
    private val aiQuizService: AiQuizService // Service này cần có cả 2 hàm generate quiz
) : ViewModel(), PassageQuizViewModel {

    private val _isLoading = MutableLiveData<Boolean>()
    override val isLoading: LiveData<Boolean> = _isLoading

    // Thay thế isFetchingPassageTest bằng currentLoadingTaskType
    private val _currentLoadingTaskType = MutableLiveData<PassageQuizViewModel.LoadingTaskType?>()
    override val currentLoadingTaskType: LiveData<PassageQuizViewModel.LoadingTaskType?> = _currentLoadingTaskType

    // Thay thế passageQuizData bằng displayableQuizContent
    private val _displayableQuizContent = MutableLiveData<DisplayableQuizContent?>()
    override val displayableQuizContent: LiveData<DisplayableQuizContent?> = _displayableQuizContent

    private val _gradingResult = MutableLiveData<AIGradingResult?>()
    override val gradingResult: LiveData<AIGradingResult?> = _gradingResult

    private val _errorMessage = MutableLiveData<String?>()
    override val errorMessage: LiveData<String?> = _errorMessage

    // Factory for injection (giữ nguyên)
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

    override fun fetchAndPrepareQuiz(topic: String, isCustom: Boolean) {
        _isLoading.value = true
        _currentLoadingTaskType.value = PassageQuizViewModel.LoadingTaskType.FETCHING_QUIZ
        _displayableQuizContent.value = null // Xóa quiz cũ
        _gradingResult.value = null       // Xóa kết quả chấm bài cũ
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val chosenType = if (Random.nextBoolean()) QuizDisplayType.FILL_THE_BLANK else QuizDisplayType.READING_COMPREHENSION
                Log.d("PassageQuizViewModel", "Chosen quiz type: $chosenType")

                var passageText: String
                var questionsList: List<QuestionData>

                var successfulFetch = false
                var attempt = 0
                val maxAttempts = 2 // Thử tối đa 2 lần (1 lần đầu + 1 retry)

                while (!successfulFetch && attempt < maxAttempts) {
                    attempt++
                    Log.d("PassageQuizViewModel", "Attempting to fetch quiz, attempt $attempt")
                    try {
                        when (chosenType) {
                            QuizDisplayType.FILL_THE_BLANK -> {
                                val fillInBlanksData = aiQuizService.generatePassageQuiz(topic, isCustom)
                                passageText = fillInBlanksData.passage
                                questionsList = fillInBlanksData.questions.map { // Chuyển PassageQuestionItem -> QuestionData
                                    QuestionData(
                                        id = it.id,
                                        // question_text cho fill-in-the-blank có thể là placeholder
                                        question_text = "Điền vào chỗ trống ${it.id.replace("blank", "")}",
                                        options = it.options,
                                        correct_answer = it.correct_answer // Giả sử PassageQuestionItem có correctAnswer
                                    )
                                }
                            }
                            QuizDisplayType.READING_COMPREHENSION -> {
                                val readingData = aiQuizService.generateReadingComprehensionQuiz(topic, isCustom)
                                passageText = readingData.passage
                                questionsList = readingData.questions.map { // Chuyển ReadingComprehensionQuestion -> QuestionData
                                    QuestionData(
                                        id = it.id,
                                        question_text = it.question_text, // question_text từ ReadingComprehensionQuestion
                                        options = it.options,
                                        correct_answer = it.correct_answer // Giả sử ReadingComprehensionQuestion có correct_answer
                                    )
                                }
                            }
                        }
                        _displayableQuizContent.postValue(DisplayableQuizContent(passageText, questionsList, chosenType))
                        successfulFetch = true
                        Log.d("PassageQuizViewModel", "Successfully fetched quiz on attempt $attempt")
                    } catch (e: Exception) {
                        Log.e("PassageQuizViewModel", "Error fetching quiz on attempt $attempt", e)
                        if (attempt >= maxAttempts) { // Nếu đã hết số lần thử
                            _errorMessage.postValue("Lỗi khi tạo bài quiz (${e.message}). Vui lòng thử lại sau.")
                        } else {
                            delay(3000) // Đợi 3 giây trước khi thử lại
                        }
                    }
                }
            } catch (e: Exception) { // Bắt lỗi ngoài vòng lặp (ví dụ lỗi từ Random)
                Log.e("PassageQuizViewModel", "Outer error in fetchAndPrepareQuiz", e)
                _errorMessage.postValue("Đã có lỗi không mong muốn xảy ra.")
            }
            finally {
                _isLoading.postValue(false)
                _currentLoadingTaskType.postValue(null)
            }
        }
    }

    // Hàm tryFetchPassageQuizOnce không còn cần thiết trực tiếp ở đây nữa
    // vì logic retry đã được tích hợp vào fetchAndPrepareQuiz.

    override fun submitAnswers(userAnswers: List<UserAnswer>, questions: List<QuestionData>) {
        if (questions.isEmpty()) {
            _errorMessage.value = "Không có câu hỏi để chấm điểm."
            return
        }

        _isLoading.value = true
        _currentLoadingTaskType.value = PassageQuizViewModel.LoadingTaskType.GRADING
        _gradingResult.value = null // Xóa kết quả cũ
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val result = aiQuizService.gradeQuiz(questions, userAnswers)
                _gradingResult.postValue(result)
            } catch (e: Exception) {
                Log.e("PassageQuizViewModel", "Error submitting answers", e)
                _errorMessage.postValue("Lỗi khi chấm bài: ${e.message}")
            } finally {
                _isLoading.postValue(false)
                _currentLoadingTaskType.postValue(null)
            }
        }
    }

    override fun clearGradingResult() {
        _gradingResult.value = null
    }

    override fun clearErrorMessage() {
        _errorMessage.value = null
    }

    // Đổi tên từ clearPassageQuizData -> clearQuizContent
    override fun clearQuizContent() {
        _displayableQuizContent.value = null
    }
}