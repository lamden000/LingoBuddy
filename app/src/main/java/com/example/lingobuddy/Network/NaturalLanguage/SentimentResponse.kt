package com.example.lingobuddy.Network.NaturalLanguage
data class AnalyzeSentimentRequest(val document: Document, val encodingType: String = "UTF8")
data class Document(val content: String, val type: String = "PLAIN_TEXT")
data class AnalyzeSentimentResponse(val documentSentiment: DocumentSentiment, val language: String)
data class DocumentSentiment(val magnitude: Float, val score: Float)