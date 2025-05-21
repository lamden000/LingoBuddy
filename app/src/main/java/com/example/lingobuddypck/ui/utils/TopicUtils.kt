package com.example.lingobuddypck.ui.utils

import android.content.Context
import android.util.Log

object TopicUtils {
    fun getRandomTopicFromAssets(context: Context): String {
        return try {
            val inputStream = context.assets.open("topics.txt")
            val topics = inputStream.bufferedReader().useLines { it.toList() }
            topics.random()
        } catch (e: Exception) {
            Log.e("TopicUtils", "Error reading topics from assets: ${e.message}")
            "General English" // Fallback topic
        }
    }
}