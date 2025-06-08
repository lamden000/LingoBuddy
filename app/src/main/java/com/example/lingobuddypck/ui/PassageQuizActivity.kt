package com.example.lingobuddypck.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import com.example.lingobuddypck.Services.QuizService.AiQuizService
import com.example.lingobuddypck.Services.QuizService.PassageQuiz.PassageQuizUIManager
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.R
import com.example.lingobuddypck.Services.QuizService.PassageQuiz.PassageQuizViews
import com.example.lingobuddypck.ViewModel.PassageQuizViewModelImpl
import com.example.lingobuddypck.Repository.FirebaseWordRepository
import com.example.lingobuddypck.utils.TaskManager
import com.google.gson.Gson

class PassageQuizActivity : AppCompatActivity() {

    private val viewModel: PassageQuizViewModelImpl by viewModels {
        PassageQuizViewModelImpl.Factory(
            AiQuizService(Gson(), RetrofitClient.instance)
        )
    }

    private lateinit var uiManager: PassageQuizUIManager
    private lateinit var views: PassageQuizViews

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passage_quiz)

        views = PassageQuizViews(
            progressBar = findViewById(R.id.progressBar),
            passageTextView = findViewById(R.id.passageTextView),
            questionsContainer = findViewById(R.id.questionsContainer),
            buttonSubmit = findViewById(R.id.buttonSubmit),
            buttonStart = findViewById(R.id.buttonStart),
            textViewResult = findViewById(R.id.textViewResult),
            scrollView = findViewById(R.id.scrollView),
            textViewLoadingHint = findViewById(R.id.textViewLoadingHint),
            textViewCountdown = findViewById(R.id.textViewCountdown),
            aiAvatar = findViewById(R.id.aiAvatar),
            recyclerView = findViewById(R.id.recyclerView),
            customTopicEditTxt = findViewById(R.id.customTopicEditTxt),
            initialStateContainer = findViewById(R.id.initialStateContainer)
        )
        intent.getStringExtra("topic")?.let { topic ->
            views.customTopicEditTxt?.setText(topic)
            // Automatically start quiz with this topic
            views.buttonStart.performClick()
        }
        val wordRepository = FirebaseWordRepository()

        uiManager = PassageQuizUIManager(
            context = this,
            lifecycleOwner = this,
            viewModel = viewModel,
            wordRepository = wordRepository,
            views = views,
            onShowNavigationBar = { },
            onHideNavigationBar = { }
        )
    }
}
