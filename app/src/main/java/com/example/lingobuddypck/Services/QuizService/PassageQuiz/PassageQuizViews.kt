package com.example.lingobuddypck.Services.QuizService.PassageQuiz

import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class PassageQuizViews(
    val progressBar: ProgressBar,
    val passageTextView: TextView,
    val questionsContainer: LinearLayout,
    val buttonSubmit: Button,
    val buttonStart: Button,
    val textViewResult: TextView,
    val scrollView: ScrollView,
    val textViewLoadingHint: TextView,
    val textViewCountdown: TextView,
    val aiAvatar: ImageView,
    val recyclerView: RecyclerView? = null,
    val customTopicEditTxt: EditText? = null,
    val initialStateContainer: LinearLayout
)
