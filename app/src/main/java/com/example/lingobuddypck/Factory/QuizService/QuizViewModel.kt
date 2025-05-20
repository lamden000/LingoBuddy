package com.example.lingobuddypck.Factory.QuizService

import androidx.lifecycle.LiveData
import com.example.lingobuddypck.Network.TogetherAI.AIGradingResult
import com.example.lingobuddypck.Network.TogetherAI.PassageQuizData
import com.example.lingobuddypck.Network.TogetherAI.QuestionData
import com.example.lingobuddypck.Network.TogetherAI.UserAnswer

interface QuizViewModel {
    val isLoading: LiveData<Boolean>
    val testQuestions: LiveData<List<QuestionData>?>
    val gradingResult: LiveData<AIGradingResult?>
    val errorMessage: LiveData<String?>
    val isFetchingTest: LiveData<Boolean>
    fun fetchTest(topic: String, isCustom: Boolean)
    fun submitAnswers(answers: List<UserAnswer>)
    fun clearGradingResult()
    fun clearErrorMessage()

    fun clearQuestions()
}

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