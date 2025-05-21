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
import com.google.gson.Gson // Your Gson instance

class PassageQuizActivity : AppCompatActivity() {

    // Use your new ViewModel implementation
    private val viewModel: PassageQuizViewModelImpl by viewModels {
        PassageQuizViewModelImpl.Factory(
            AiQuizService(Gson(), RetrofitClient.instance) // Ensure RetrofitClient.togetherApi provides your TogetherApi instance
        )
    }

    private lateinit var uiManager: PassageQuizUIManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passage_quiz)

        // Instantiate the PassageQuizViews data class with your layout elements
        val views = PassageQuizViews(
            progressBar = findViewById(R.id.progressBar),
            passageTextView = findViewById(R.id.passageTextView) ,
            questionsContainer = findViewById(R.id.questionsContainer),
            buttonSubmit = findViewById(R.id.buttonSubmit),
            buttonStart = findViewById(R.id.buttonStart),
            textViewResult = findViewById(R.id.textViewResult),
            scrollView = findViewById(R.id.scrollView),
            textViewLoadingHint = findViewById(R.id.textViewLoadingHint),
            textViewCountdown = findViewById(R.id.textViewCountdown),
            aiAvatar = findViewById(R.id.aiAvatar),
            recyclerView = findViewById(R.id.recyclerView), // Optional, can be null
            customTopicEditTxt = findViewById(R.id.customTopicEditTxt), // Optional, can be null
            initialStateContainer = findViewById(R.id.initialStateContainer) // Important for managing start UI
        )

        // Initialize FirebaseWordRepository - adjust this based on your actual DI or instantiation
        val wordRepository = FirebaseWordRepository()

        // Instantiate your new UIManager
        uiManager = PassageQuizUIManager(
            context = this,
            lifecycleOwner = this,
            viewModel = viewModel,
            wordRepository = wordRepository,
            views = views,
            onShowNavigationBar = { /* Implement navigation bar show logic here if needed */ },
            onHideNavigationBar = { /* Implement navigation bar hide logic here if needed */ }
        )

        // Observe LiveData from the ViewModel to update the Activity's UI
        // (Though UIManager handles most, sometimes activities have specific needs)
        // For example, if you want to update the toolbar title based on loading state or quiz completion.
    }
}
