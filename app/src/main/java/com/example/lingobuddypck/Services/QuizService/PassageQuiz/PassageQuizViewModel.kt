package com.example.lingobuddypck.Services.QuizService.PassageQuiz

import androidx.lifecycle.LiveData
import com.example.lingobuddypck.Services.AIGradingResult
import com.example.lingobuddypck.Services.PassageQuizData
import com.example.lingobuddypck.Services.QuestionData
import com.example.lingobuddypck.Services.UserAnswer
import com.example.lingobuddypck.data.DisplayableQuizContent


interface PassageQuizViewModel {
    val isLoading: LiveData<Boolean> // Trạng thái loading chung
    val currentLoadingTaskType: LiveData<LoadingTaskType?> // Cho UIManager biết cụ thể đang làm gì
    val displayableQuizContent: LiveData<DisplayableQuizContent?> // Dữ liệu quiz để hiển thị
    val gradingResult: LiveData<AIGradingResult?>
    val errorMessage: LiveData<String?>

    fun fetchAndPrepareQuiz(topic: String, isCustom: Boolean) // Đã đổi tên và logic
    fun submitAnswers(answers: List<UserAnswer>, questions: List<QuestionData>) // Đã đổi tên (tùy chọn)
    fun clearGradingResult()
    fun clearErrorMessage()
    fun clearQuizContent() // Đã đổi tên

    // Enum để chỉ rõ loại tác vụ đang loading
    enum class LoadingTaskType {
        FETCHING_QUIZ,
        GRADING
    }
}