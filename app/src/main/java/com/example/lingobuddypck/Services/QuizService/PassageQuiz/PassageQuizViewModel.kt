package com.example.lingobuddypck.Services.QuizService.PassageQuiz

import androidx.lifecycle.LiveData
import com.example.lingobuddypck.Services.AIGradingResult
import com.example.lingobuddypck.Services.PassageQuizData
import com.example.lingobuddypck.Services.QuestionData
import com.example.lingobuddypck.Services.UserAnswer

interface PassageQuizViewModel {
    val isLoading: LiveData<Boolean>
    val isFetchingPassageTest: LiveData<Boolean> // Specific loading state
    val passageQuizData: LiveData<PassageQuizData?>
    val gradingResult: LiveData<AIGradingResult?>
    val errorMessage: LiveData<String?>

    fun fetchPassageTest(topic: String, isCustom: Boolean)
    fun submitPassageAnswers(answers: List<UserAnswer>, questions: List<QuestionData>) // Need questions to grade
    fun clearGradingResult()
    fun clearErrorMessage()
    fun clearPassageQuizData() // New clear function
}