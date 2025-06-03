package com.example.lingobuddypck.data

import com.example.lingobuddypck.Services.QuestionData

data class DisplayableQuizContent(
    val passage: String,
    val questions: List<QuestionData>, // Sử dụng QuestionData chung
    val type: QuizDisplayType
)
