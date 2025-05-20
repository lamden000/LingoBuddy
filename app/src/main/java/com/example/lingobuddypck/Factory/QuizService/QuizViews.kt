package com.example.lingobuddypck.Factory.QuizService

import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class QuizViews(
    val progressBar: ProgressBar,
    val questionsContainer: LinearLayout,
    val buttonSubmit: Button,
    val buttonStart: Button,
    val textViewResult: TextView,
    val scrollView: ScrollView,
    val textViewLoadingHint: TextView,
    val textViewCountdown: TextView,
    val aiAvatar: ImageView,
    val recyclerView: RecyclerView? = null,
    val customTopicEditTxt: EditText? = null
)

data class PassageQuizViews(
    val progressBar: ProgressBar,
    val passageTextView: TextView, // To display the passage text
    val questionsContainer: LinearLayout, // Container for individual question views
    val buttonSubmit: Button,
    val buttonStart: Button,
    val textViewResult: TextView,
    val scrollView: ScrollView,
    val textViewLoadingHint: TextView,
    val textViewCountdown: TextView,
    val aiAvatar: ImageView,
    // Optional UI elements for initial state / topic selection:
    val recyclerView: RecyclerView? = null,
    val customTopicEditTxt: EditText? = null,
    val initialStateContainer: LinearLayout // The container for initial state UI elements
)