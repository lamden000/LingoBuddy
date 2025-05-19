package com.example.lingobuddypck.Factory.QuizService

import androidx.lifecycle.LiveData
import com.example.lingobuddypck.Network.TogetherAI.AIGradingResult
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
