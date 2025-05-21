package com.example.lingobuddypck.Services.QuizService.PassageQuiz

import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.example.lingobuddypck.Services.QuestionData
import com.example.lingobuddypck.Services.UserAnswer
import com.example.lingobuddypck.R
import com.example.lingobuddypck.Repository.FirebaseWordRepository
import com.example.lingobuddypck.ui.utils.TopicUtils
import com.example.lingobuddypck.ui.utils.enableSelectableSaveAction

class PassageQuizUIManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: PassageQuizViewModel,
    private val wordRepository: FirebaseWordRepository,
    private val views: PassageQuizViews,
    private val onShowNavigationBar: (() -> Unit)? = null,
    private val onHideNavigationBar: (() -> Unit)? = null
) {

    private var countdownTimer: CountDownTimer? = null
    private val questionViews = mutableMapOf<String, RadioGroup>()
    private val feedbackViews = mutableMapOf<String, TextView>()
    private var isShowingGradingResult = false
    private var originalButtonText: String

    init {
        originalButtonText = views.buttonStart.text.toString()
        setupUI()
        setupObservers()
        resetUIForStart() // Set initial UI state
    }

    private fun setupUI() {
        views.buttonStart.setOnClickListener {
            startNewPassageQuizFlow()
        }

        views.buttonSubmit.setOnClickListener {
            if (isShowingGradingResult) {
                confirmGradingResult()
            } else {
                submitUserAnswers()
            }
        }
    }

    private fun startNewPassageQuizFlow() {
        val topic = views.customTopicEditTxt?.text.toString().trim()
        val isCustom = topic.isNotBlank()
        viewModel.fetchPassageTest(if (isCustom) topic else TopicUtils.getRandomTopicFromAssets(context), isCustom)
        resetUIForLoading()
    }

    private fun submitUserAnswers() {
        val userAnswers = mutableListOf<UserAnswer>()
        val currentQuestions = viewModel.passageQuizData.value?.questions

        if (currentQuestions.isNullOrEmpty()) {
            Toast.makeText(context, "Không có câu hỏi để nộp.", Toast.LENGTH_SHORT).show()
            return
        }

        var allAnswered = true
        currentQuestions.forEach { question ->
            val radioGroup = questionViews[question.id]
            val selectedRadioButtonId = radioGroup?.checkedRadioButtonId ?: -1
            if (selectedRadioButtonId != -1) {
                val selectedRadioButton = radioGroup?.findViewById<RadioButton>(selectedRadioButtonId)
                val answerKey = selectedRadioButton?.tag as? String
                if (answerKey != null) {
                    userAnswers.add(UserAnswer(question.id, answerKey))
                } else {
                    allAnswered = false
                }
            } else {
                allAnswered = false
            }
        }

        if (allAnswered && userAnswers.size == currentQuestions.size) {
            viewModel.submitPassageAnswers(userAnswers, currentQuestions)
        } else {
            Toast.makeText(context, "Vui lòng trả lời tất cả các câu hỏi.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmGradingResult() {
        isShowingGradingResult = false
        views.textViewResult.isVisible = false
        feedbackViews.values.forEach { it.isVisible = false }
        views.buttonSubmit.text = "Nộp bài"
        views.buttonSubmit.isVisible = false
        views.buttonStart.isVisible = true
        views.scrollView.isVisible = false
        views.initialStateContainer.isVisible = true // Show initial state UI
        views.scrollView.alpha = 1f
        onShowNavigationBar?.invoke()
        viewModel.clearGradingResult()
        viewModel.clearPassageQuizData() // Clear passage data in ViewModel
    }

    private fun setupObservers() {
        views.passageTextView.enableSelectableSaveAction(context) { selectedText, note ->
            wordRepository.saveWord(
                word = selectedText,
                note = note,
                onSuccess = {
                    Toast.makeText(context, "Đã lưu \"$selectedText\"!", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(context, "Lỗi khi lưu từ: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }

        observeLoading()
        observeFetchingState()
        observePassageQuizData()
        observeGradingResult()
        observeErrorMessage()
    }

    private fun observeLoading() {
        viewModel.isLoading.observe(lifecycleOwner) { isLoading ->
            updateLoadingUI(isLoading)
            if (isLoading) startCountdown() else stopCountdown()
        }
    }

    private fun updateLoadingUI(isLoading: Boolean) {
        views.progressBar.isVisible = isLoading
        views.textViewLoadingHint.isVisible = isLoading
        views.textViewCountdown.isVisible = isLoading
        views.aiAvatar.isVisible = isLoading

        views.buttonStart.isEnabled = !isLoading
        views.buttonSubmit.isEnabled = !isLoading
        views.scrollView.alpha = if (isLoading) 0.1f else 1f

        if (!isLoading) {
            views.textViewCountdown.isVisible = false
            if (viewModel.passageQuizData.value != null && !isShowingGradingResult) {
                views.buttonSubmit.isVisible = true
                views.buttonSubmit.text = "Nộp bài"
                views.buttonStart.isVisible = false
                views.initialStateContainer.isVisible = false
                onHideNavigationBar?.invoke()
            } else if (viewModel.gradingResult.value == null && viewModel.errorMessage.value != null) {
                resetUIForStart()
            }
        }
    }

    private fun observeFetchingState() {
        viewModel.isFetchingPassageTest.observe(lifecycleOwner) { isFetchingTest ->
            val fetchingMessages = listOf(
                "⌛ Một chút thôi... AI đang gãi đầu nghĩ đoạn văn.",
                "🧠 Đang suy nghĩ... đừng rời đi nhé!",
                "🤔 Đang tạo đoạn văn và câu hỏi siêu ngầu...",
                "🌀 Trí tuệ nhân tạo đang uống cà phê...",
                "📚 Đang tham khảo tài liệu cho đoạn văn."
            )
            val gradingMessages = listOf(
                "🤖 AI đang chấm điểm như thầy giáo khó tính.",
                "🧘‍♂️ Đang thiền để đánh giá công bằng.",
                "⚖️ Cân đo từng đáp án chính xác.",
                "💡 Đang kiểm tra từng câu trả lời.",
                "📉 Chấm xong sai là tụt mood liền á..."
            )
            val text = if (isFetchingTest) fetchingMessages.random() else gradingMessages.random()
            views.textViewLoadingHint.text = text
        }
    }

    private fun observePassageQuizData() {
        viewModel.passageQuizData.observe(lifecycleOwner) { passageQuizData ->
            if (isShowingGradingResult) return@observe

            views.questionsContainer.removeAllViews()
            questionViews.clear()
            feedbackViews.clear()

            if (passageQuizData != null) {
                views.passageTextView.text = passageQuizData.passage
                renderQuestions(passageQuizData.questions)
            } else {
                views.scrollView.isVisible = false
                views.buttonSubmit.isVisible = false
                if (viewModel.isLoading.value == false) resetUIForStart()
            }
        }
    }

    private fun renderQuestions(questions: List<QuestionData>) {
        views.scrollView.isVisible = true
        views.initialStateContainer.isVisible = false
        onHideNavigationBar?.invoke()

        questions.forEachIndexed { index, q -> addQuestionToLayout(index, q) }
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.scroll_bottom_padding) // Assuming you have this dimen
            )
        }
        views.questionsContainer.addView(spacer)
        if (viewModel.isLoading.value == false) {
            views.buttonSubmit.isVisible = true
            views.buttonSubmit.text = "Nộp bài"
            views.buttonStart.isVisible = false
        }
        views.scrollView.post { views.scrollView.fullScroll(ScrollView.FOCUS_UP) }
    }

    private fun observeGradingResult() {
        viewModel.gradingResult.observe(lifecycleOwner) { result ->
            if (result == null) {
                views.textViewResult.isVisible = false
                return@observe
            }

            isShowingGradingResult = true
            val correct = result.score
            val total = result.total_questions

            views.textViewResult.text = "Kết quả: $correct/$total câu đúng."
            views.textViewResult.isVisible = true
            views.buttonSubmit.isVisible = true
            views.buttonSubmit.isEnabled = true
            views.buttonStart.isVisible = false

            feedbackViews.values.forEach { it.isVisible = false } // Clear previous feedback

            result.feedback.forEach { (qid, fb) ->
                val question = viewModel.passageQuizData.value?.questions?.find { it.id == qid }
                val correctDisplay = question?.options?.get(question.correct_answer)?.let {
                    "${question.correct_answer.uppercase()}. $it"
                } ?: question?.correct_answer?.uppercase() ?: "N/A"

                feedbackViews[qid]?.apply {
                    isVisible = true
                    if (fb.status == "correct") {
                        setTextColor(Color.parseColor("#228B22")) // Green
                        text = "✅ Trả lời đúng"
                    } else {
                        setTextColor(Color.parseColor("#B22222")) // Red
                        text = "❌ Trả lời sai.\nĐáp án đúng: $correctDisplay.\nGiải thích: ${fb.explanation ?: "Không có giải thích."}"
                    }
                }
            }

            views.scrollView.post { views.scrollView.fullScroll(ScrollView.FOCUS_UP) }
            views.buttonSubmit.text = "Xác nhận"
        }
    }

    private fun observeErrorMessage() {
        viewModel.errorMessage.observe(lifecycleOwner) { message ->
            message?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
                if (viewModel.isLoading.value == false) resetUIForStart()
            }
        }
    }

    private fun startCountdown() {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                views.textViewCountdown.text = "Thời gian còn lại: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                views.textViewCountdown.text = "⏳ Có vẻ đang mất nhiều thời gian... hãy kiên nhẫn!"
            }
        }.start()
    }

    private fun stopCountdown() {
        countdownTimer?.cancel()
    }

    private fun resetUIForStart() {
        views.questionsContainer.removeAllViews()
        questionViews.clear()
        feedbackViews.clear()
        views.scrollView.isVisible = false
        views.buttonSubmit.isVisible = false
        views.textViewResult.isVisible = false
        views.progressBar.isVisible = false
        views.textViewLoadingHint.isVisible = false
        views.textViewCountdown.isVisible = false
        views.aiAvatar.isVisible = false
        views.scrollView.alpha = 1f
        views.buttonStart.isVisible = true
        views.initialStateContainer.isVisible = true // Show initial state UI
        views.recyclerView?.isVisible = true // If you use it for topics
        views.customTopicEditTxt?.isVisible = true // If you use it for custom topics
        onShowNavigationBar?.invoke()
        views.buttonStart.text = originalButtonText
    }

    private fun resetUIForLoading() {
        views.scrollView.isVisible = false
        views.buttonSubmit.isVisible = false
        views.buttonStart.isVisible = false
        views.initialStateContainer.isVisible = false
        views.textViewResult.isVisible = false
        feedbackViews.values.forEach { it.isVisible = false }
        onHideNavigationBar?.invoke()
        views.progressBar.isVisible = true
        views.textViewLoadingHint.isVisible = true
        views.textViewCountdown.isVisible = true
        views.aiAvatar.isVisible = true
    }

    private fun addQuestionToLayout(index: Int, questionData: QuestionData) {
        val inflater = LayoutInflater.from(context)
        val questionView = inflater.inflate(R.layout.item_test_question, views.questionsContainer, false)

        val tvQuestionNumber = questionView.findViewById<TextView>(R.id.textViewQuestionNumber)
        val tvQuestionContent = questionView.findViewById<TextView>(R.id.textViewQuestionContent) // Keep this reference
        val rgOptions = questionView.findViewById<RadioGroup>(R.id.radioGroupOptions)
        val tvFeedback = questionView.findViewById<TextView>(R.id.textViewFeedback)

        // CORRECTED LINE: Using proper Kotlin string interpolation
        tvQuestionNumber.text = "Blank ${index + 1}:" // Clearly indicate the blank number

        // CORRECTED LINE: Standard Kotlin for setting visibility
        tvQuestionContent.visibility = View.GONE // <--- HIDE THIS TEXTVIEW

        val sortedOptions = questionData.options.entries.sortedBy { it.key }
        sortedOptions.forEach { (optionKey, optionText) ->
            val radioButton = RadioButton(context)
            radioButton.text = "${optionKey.uppercase()}. $optionText"
            radioButton.tag = optionKey
            radioButton.id = View.generateViewId()
            rgOptions.addView(radioButton)
        }

        views.questionsContainer.addView(questionView)
        questionViews[questionData.id] = rgOptions
        feedbackViews[questionData.id] = tvFeedback
    }

}