package com.example.lingobuddypck


import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

import com.example.lingobuddypck.Factory.QuizService.AiQuizService
import com.example.lingobuddypck.Factory.QuizService.QuizViewModel
import com.example.lingobuddypck.Factory.QuizService.QuizViews
import com.example.lingobuddypck.Factory.QuizService.TestUIManager
import com.example.lingobuddypck.Network.RetrofitClient

import com.example.lingobuddypck.ViewModel.Repository.FirebaseWordRepository
import com.example.lingobuddypck.ViewModel.TestViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import com.google.gson.Gson

class TestActivity : AppCompatActivity() {

    private val wordRepository = FirebaseWordRepository()
    private val aiQuizService: AiQuizService by lazy {
        AiQuizService(Gson(), RetrofitClient.instance)
    }
    private val viewModel: TestViewModel by viewModels {
        TestViewModel.Factory(aiQuizService)
    }
    private lateinit var uiManager:TestUIManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        // Initialize QuizViews with UI elements from the layout
        val quizViews = QuizViews(
            progressBar = findViewById(R.id.progressBarTest),
            questionsContainer = findViewById(R.id.questionsContainerLayout),
            buttonSubmit = findViewById(R.id.buttonSubmitTest),
            buttonStart = findViewById(R.id.buttonStartTest),
            textViewResult = findViewById(R.id.textViewTestResult),
            scrollView = findViewById(R.id.scrollViewTest),
            textViewLoadingHint = findViewById(R.id.textViewLoadingHint),
            textViewCountdown = findViewById(R.id.textViewCountdown),
            aiAvatar = findViewById(R.id.aiAvatarLoading),
            customTopicEditTxt = findViewById(R.id.editTextCustomTopic)
        )

        uiManager= TestUIManager(
            context = this,
            lifecycleOwner = this,
            viewModel = viewModel as QuizViewModel, // Cast to QuizViewModel
            wordRepository = wordRepository,
            views = quizViews,
            onShowConfirmationDialog = { showConfirmationMakeTestDialog() } // Optional: Add confirmation dialog
        )
    }

    private fun showConfirmationMakeTestDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Bắt đầu bài kiểm tra")
            .setMessage("Bạn đã sẵn sàng làm bài kiểm tra chưa?")
            .setPositiveButton("Rồi") { dialog, _ ->
                uiManager.startProficiencyTestMode()
                dialog.dismiss()
            }
            .setNegativeButton("Chưa") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}