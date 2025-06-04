package com.example.lingobuddypck.data

data class ImageQuiz(
    val imageDescription: String,
    val questions: List<ImageQuizQuestion>
)

data class ImageQuizQuestion(
    val id: String,
    val question: String,
    val options: Map<String, String>,
    val correctAnswer: String,
    val explanation: String
) 