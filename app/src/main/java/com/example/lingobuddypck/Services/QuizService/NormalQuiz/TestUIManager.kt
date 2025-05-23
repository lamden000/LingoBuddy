package com.example.lingobuddypck.Services.QuizService.NormalQuiz


import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.util.Log
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
import com.example.lingobuddypck.utils.TopicUtils
import com.example.lingobuddypck.utils.enableSelectableSaveAction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore


/**
 * Utility class to manage quiz UI and logic, reusable across activities and fragments.
 */
class TestUIManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: QuizViewModel,
    private val wordRepository: FirebaseWordRepository,
    private val views: QuizViews,
    private val onShowConfirmationDialog: (() -> Unit)? = null,
    private val onShowNavigationBar: (() -> Unit)? = null,
    private val onHideNavigationBar: (() -> Unit)? = null
) {

    private var countdownTimer: CountDownTimer? = null
    private val questionViews = mutableMapOf<String, RadioGroup>()
    private val feedbackViews = mutableMapOf<String, TextView>()
    private var isShowingGradingResult = false
    private var originalButtonText:String

    private var isProficiencyTestMode = false
    private var currentTestCount = 0
    private var totalCorrectAnswers = 0
    private var totalQuestionsAnswered = 0
    private val maxTests = 5

    init {
        originalButtonText=views.buttonStart.text.toString()
        setupUI()
        setupObservers()
    }

    private fun setupUI() {
        views.buttonStart.setOnClickListener {
            onShowConfirmationDialog?.invoke() ?: startNewTestFlow()
        }

        views.buttonSubmit.setOnClickListener {
            if (isShowingGradingResult) {
                // Trạng thái: Kết quả đang được hiển thị.
                // Hành động của nút: xác nhận kết quả hoặc chuyển sang bước tiếp theo.
                if (isProficiencyTestMode) {
                    if (currentTestCount < maxTests - 1) {
                        // Hành động: Chuyển sang bài kiểm tra tiếp theo trong chuỗi proficiency.
                        currentTestCount++
                        isShowingGradingResult = false // Quan trọng: Để lần nhấp submit tiếp theo là để nộp bài.
                        viewModel.clearGradingResult() // Xóa kết quả cũ.

                        // Đặt lại các thành phần UI liên quan đến việc hiển thị kết quả.
                        views.textViewResult.isVisible = false
                        feedbackViews.values.forEach { it.isVisible = false }
                        // Giao diện tải sẽ được kích hoạt từ startNextProficiencyTest().
                        // Văn bản nút sẽ được đặt thành "Nộp bài" bởi updateLoadingUI/renderQuestions.
                        startNextProficiencyTest()
                    } else {
                        // Hành động: Hiển thị điểm proficiency cuối cùng và kết thúc chế độ proficiency.
                        isProficiencyTestMode = false // Kết thúc proficiency mode.
                        val finalScore = (totalCorrectAnswers * 2).coerceAtMost(100) // Giả sử công thức này đúng.
                        saveProficiencyTestResult(finalScore)
                        // Bây giờ, xác nhận và đặt lại về trạng thái màn hình ban đầu.
                        confirmGradingResult()
                    }
                } else {
                    // Hành động: Chế độ không phải proficiency, xác nhận kết quả và đặt lại.
                    confirmGradingResult()
                }
            } else {
                // Trạng thái: Câu hỏi đang được hiển thị, chờ nộp bài.
                // Hành động: Nộp câu trả lời cho bài kiểm tra hiện tại.
                submitUserAnswers()
            }
        }
        resetUIForStart()
    }

    fun startProficiencyTestMode() {
        isProficiencyTestMode = true
        currentTestCount = 0
        totalCorrectAnswers = 0
        totalQuestionsAnswered = 0
        startNextProficiencyTest()
    }

    private fun startNextProficiencyTest() {
        viewModel.fetchTest(TopicUtils.getRandomTopicFromAssets(context), false)
        resetUIForLoading()
    }

    fun startNewTestFlow() {
        viewModel.fetchTest("null",true)
        resetUIForLoading()
    }

    private fun submitUserAnswers() {
        val userAnswers = mutableListOf<UserAnswer>()
        var allAnswered = true

        questionViews.forEach { (questionId, radioGroup) ->
            val selectedRadioButtonId = radioGroup.checkedRadioButtonId
            if (selectedRadioButtonId != -1) {
                val selectedRadioButton = radioGroup.findViewById<RadioButton>(selectedRadioButtonId)
                val answerKey = selectedRadioButton.tag as? String
                if (answerKey != null) {
                    userAnswers.add(UserAnswer(questionId, answerKey))
                } else {
                    allAnswered = false
                }
            } else {
                allAnswered = false
            }
        }

        if (allAnswered && userAnswers.size == viewModel.testQuestions.value?.size) {
            viewModel.submitAnswers(userAnswers)
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
        views.recyclerView?.isVisible = true
        views.scrollView.alpha = 1f
        onShowNavigationBar?.invoke()
        viewModel.clearGradingResult()
        viewModel.clearQuestions()
    }

    private fun setupObservers() {
        observeLoading()
        observeFetchingTest()
        observeTestQuestions()
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
        views.buttonStart.isEnabled = !isLoading
        views.buttonSubmit.isEnabled = !isLoading
        views.scrollView.alpha = if (isLoading) 0.1f else 1f
        views.aiAvatar.isVisible = isLoading

        if (!isLoading) {
            views.textViewCountdown.isVisible = false
            if (viewModel.testQuestions.value != null && !isShowingGradingResult) {
                views.buttonSubmit.isVisible = true
                views.buttonSubmit.text = "Nộp bài"
                views.buttonStart.isVisible = false
                views.recyclerView?.isVisible = false
                onHideNavigationBar?.invoke()
            } else if (viewModel.gradingResult.value == null && viewModel.errorMessage.value != null) {
                resetUIForStart()
            }
        }
    }

    private fun observeFetchingTest() {
        viewModel.isFetchingTest.observe(lifecycleOwner) { isFetchingTest ->
            val fetchingTestMessages = listOf(
                "⌛ Một chút thôi... AI đang gãi đầu nghĩ câu hỏi.",
                "🧠 Đang suy nghĩ... đừng rời đi nhé!",
                "🤔 Đang tạo câu hỏi siêu ngầu...",
                "🌀 Trí tuệ nhân tạo đang uống cà phê...",
                "📚 Đang tham khảo vài triệu tài liệu."
            )
            val gradingMessages = listOf(
                "🤖 AI đang chấm điểm như thầy giáo khó tính.",
                "🧘‍♂️ Đang thiền để đánh giá công bằng.",
                "⚖️ Cân đo từng đáp án chính xác.",
                "💡 Đang kiểm tra từng câu trả lời.",
                "📉 Chấm xong sai là tụt mood liền á..."
            )
            val text = if (isFetchingTest) fetchingTestMessages.random() else gradingMessages.random()
            views.textViewLoadingHint.text = text
        }
    }

    private fun observeTestQuestions() {
        viewModel.testQuestions.observe(lifecycleOwner) { questions ->
            if (isShowingGradingResult) return@observe
            views.questionsContainer.removeAllViews()
            questionViews.clear()
            feedbackViews.clear()

            if (!questions.isNullOrEmpty()) {
                renderQuestions(questions)
            } else {
                views.scrollView.isVisible = false
                views.buttonSubmit.isVisible = false
                if (viewModel.isLoading.value == false) resetUIForStart()
            }
        }
    }

    private fun renderQuestions(questions: List<QuestionData>) {
        views.scrollView.isVisible = true
        views.recyclerView?.isVisible = false
        onHideNavigationBar?.invoke()

        questions.forEachIndexed { index, q -> addQuestionToLayout(index, q) }
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.scroll_bottom_padding)
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
            totalCorrectAnswers += correct
            totalQuestionsAnswered += total

            views.textViewResult.text = "Kết quả: $correct/$total câu đúng."
            views.textViewResult.isVisible = true
            views.buttonSubmit.isVisible = true
            views.buttonSubmit.isEnabled = true
            views.buttonStart.isVisible = false

            result.feedback.forEach { (qid, fb) ->
                val question = viewModel.testQuestions.value?.find { it.id == qid }
                val correctDisplay = question?.options?.get(question.correct_answer)?.let {
                    "${question.correct_answer.uppercase()}. $it"
                } ?: question?.correct_answer?.uppercase() ?: "N/A"

                feedbackViews[qid]?.apply {
                    isVisible = true
                    if (fb.status == "correct") {
                        setTextColor(Color.parseColor("#228B22"))
                        text = "✅ Trả lời đúng"
                    } else {
                        setTextColor(Color.parseColor("#B22222"))
                        text = "❌ Trả lời sai.\nĐáp án đúng: $correctDisplay.\nGiải thích: ${fb.explanation ?: "Không có giải thích."}"
                    }
                }
            }

            views.scrollView.post { views.scrollView.fullScroll(ScrollView.FOCUS_UP) }
            updateSubmitButtonTextAfterGrading()
        }
    }

    private fun updateSubmitButtonTextAfterGrading() {
        views.buttonSubmit.text = when {
            isProficiencyTestMode && currentTestCount < maxTests - 1 -> "Bài tiếp theo (${currentTestCount + 2}/$maxTests)" // currentTestCount là số bài đã hoàn thành (0-indexed)
            isProficiencyTestMode && currentTestCount == maxTests - 1 -> "Xem điểm đánh giá"
            else -> "Xác nhận" // Dành cho chế độ không phải proficiency hoặc sau khi hoàn thành proficiency
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

    // Countdown helper
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

    private fun saveProficiencyTestResult(score: Int) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val db = FirebaseFirestore.getInstance()

        if (firebaseUser == null) {
            Toast.makeText(context, "Lỗi: Người dùng chưa đăng nhập.", Toast.LENGTH_LONG).show()
            return
        }

        val userId = firebaseUser.uid

        val testResult = hashMapOf(
            "score" to score,
            "timestamp" to FieldValue.serverTimestamp(),
            "userId" to userId
        )

        db.collection("users").document(userId)
            .collection("proficiencyTestResults")
            .add(testResult)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(context, "Điểm đánh giá đã được lưu: $score/100", Toast.LENGTH_LONG).show()
                Log.d("TestUIManager", "Proficiency result saved with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Lỗi khi lưu điểm: ${e.message}", Toast.LENGTH_LONG).show()
                Log.w("TestUIManager", "Error saving proficiency result", e)
            }
    }
    private fun resetUIForStart() {
        views.questionsContainer.removeAllViews()
        questionViews.clear()
        feedbackViews.clear()
        views.scrollView.isVisible = false
        views.buttonSubmit.isVisible = false
        views.textViewResult.isVisible = false
        views.textViewLoadingHint.isVisible = false
        views.textViewCountdown.isVisible = false
        views.aiAvatar.isVisible = false
        views.scrollView.alpha = 1f
        views.buttonStart.isVisible = true
        views.recyclerView?.isVisible = true
        onShowNavigationBar?.invoke()
        views.buttonStart.text = originalButtonText
    }

    private fun resetUIForLoading() {
        views.scrollView.isVisible = true
        views.buttonSubmit.isVisible = false
        views.buttonStart.isVisible = false
        views.recyclerView?.isVisible = false
        views.textViewResult.isVisible = false
        feedbackViews.values.forEach { it.isVisible = false }
        onHideNavigationBar?.invoke()
    }

    private fun addQuestionToLayout(index: Int, questionData: QuestionData) {
        val inflater = LayoutInflater.from(context)
        val questionView = inflater.inflate(R.layout.item_test_question, views.questionsContainer, false)

        val tvQuestionNumber = questionView.findViewById<TextView>(R.id.textViewQuestionNumber)
        val tvQuestionContent = questionView.findViewById<TextView>(R.id.textViewQuestionContent)
        val rgOptions = questionView.findViewById<RadioGroup>(R.id.radioGroupOptions)
        val tvFeedback = questionView.findViewById<TextView>(R.id.textViewFeedback)

        tvQuestionNumber.text = "Câu ${index + 1}:"
        tvQuestionContent.text = questionData.question_text
        tvQuestionContent.enableSelectableSaveAction(context) { selectedText, note ->
            wordRepository.saveWord(
                word = selectedText,
                note = note,
                onSuccess = { Toast.makeText(context, "Đã lưu vào từ điển của bạn.", Toast.LENGTH_SHORT).show() },
                onFailure = { e -> Toast.makeText(context, "Lỗi khi lưu: ${e.message}", Toast.LENGTH_SHORT).show() }
            )
        }

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