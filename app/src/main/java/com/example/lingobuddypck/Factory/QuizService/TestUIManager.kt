package com.example.lingobuddypck.Factory.QuizService


import android.content.Context
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.Network.TogetherAI.AIGradingResult

import com.example.lingobuddypck.Network.TogetherAI.QuestionData
import com.example.lingobuddypck.Network.TogetherAI.UserAnswer
import com.example.lingobuddypck.R
import com.example.lingobuddypck.ViewModel.Repository.FirebaseWordRepository
import com.example.lingobuddypck.ui.utils.enableSelectableSaveAction


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
                confirmGradingResult()
            } else {
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
        viewModel.fetchTest(getRandomTopicFromAssets(context), false)
        resetUIForLoading()
    }

    fun startNewTestFlow() {
        if(views.customTopicEditTxt!=null)
        {
            if (views.customTopicEditTxt.text.toString().isNotBlank())
                viewModel.fetchTest(views.customTopicEditTxt.text.toString(),true)
            else
                viewModel.fetchTest(getRandomTopicFromAssets(context),false)
        }
        else
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
        views.customTopicEditTxt?.isVisible = true
        views.scrollView.alpha = 1f
        onShowNavigationBar?.invoke()
        viewModel.clearGradingResult()
        viewModel.clearQuestions()
    }

    private fun setupObservers() {

        viewModel.isLoading.observe(lifecycleOwner) { isLoading ->
            views.progressBar.isVisible = isLoading
            views.textViewLoadingHint.isVisible = isLoading
            views.textViewCountdown.isVisible = isLoading
            views.buttonStart.isEnabled = !isLoading
            views.buttonSubmit.isEnabled = !isLoading
            val fetchingTestMessages = listOf(
                "⌛ Một chút thôi... AI đang gãi đầu nghĩ câu hỏi.",
                "🧠 Đang suy nghĩ... đừng rời đi nhé!",
                "🤔 Đang tạo đề bài siêu ngầu cho bạn...",
                "🌀 Trí tuệ nhân tạo đang uống cà phê...",
                "📚 Đang tham khảo vài triệu tài liệu. Bình tĩnh."
            )
            val gradingMessages = listOf(
                "🤖 AI đang lục lại trí nhớ như thầy giáo khó tính.",
                "🧘‍♂️ Đang thiền để đánh giá công bằng tuyệt đối.",
                "⚖️ Cân đo từng đáp án như đo lường vũ trụ.",
                "💡 Đang soi từng dòng suy nghĩ bạn vừa có.",
                "📉 Chấm xong sai là tụt mood liền á...",
                "🎯 Nhắm trúng lỗi sai rồi, giờ đang vẽ vòng tròn đỏ.",
                "📡 Kết nối server chấm điểm... "
            )

            viewModel.isFetchingTest.observe(lifecycleOwner){isFetchingTest->
                if (isFetchingTest)
                    views.textViewLoadingHint.text = fetchingTestMessages.random()
                else
                    views.textViewLoadingHint.text = gradingMessages.random()
            }
            if (isLoading) {
                views.scrollView.alpha = 0.1f
                views.aiAvatar.isVisible = true
                countdownTimer?.cancel()
                countdownTimer = object : CountDownTimer(60000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        views.textViewCountdown.text = "Thời gian còn lại: ${millisUntilFinished / 1000}s"
                    }

                    override fun onFinish() {
                        views.textViewCountdown.text = "⏳ Có vẻ đang mất nhiều thời gian... hãy kiên nhẫn!"
                    }
                }.start()
            } else {
                countdownTimer?.cancel()
                views.textViewCountdown.isVisible = false
                views.aiAvatar.isVisible = false
                views.scrollView.alpha = 1f

                if (viewModel.testQuestions.value != null && !isShowingGradingResult) {
                    views.buttonSubmit.isVisible = true
                    views.buttonSubmit.text = "Nộp bài"
                    views.buttonStart.isVisible = false
                    views.customTopicEditTxt?.isVisible = false
                    views.recyclerView?.isVisible = false
                    onHideNavigationBar?.invoke()
                } else if (viewModel.gradingResult.value == null && viewModel.errorMessage.value != null) {
                    resetUIForStart()
                }
            }
        }

        viewModel.testQuestions.observe(lifecycleOwner) { questions ->
            if (!isShowingGradingResult) {
                views.questionsContainer.removeAllViews()
                questionViews.clear()
                feedbackViews.clear()

                if (!questions.isNullOrEmpty()) {
                    views.scrollView.isVisible = true
                    views.customTopicEditTxt?.isVisible = false
                    views.recyclerView?.isVisible = false
                    onHideNavigationBar?.invoke()

                    questions.forEachIndexed { index, questionData ->
                        addQuestionToLayout(index, questionData)
                    }
                    val bottomSpacer = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            context.resources.getDimensionPixelSize(R.dimen.scroll_bottom_padding)
                        )
                    }
                    views.questionsContainer.addView(bottomSpacer)
                    if (viewModel.isLoading.value == false) {
                        views.buttonSubmit.isVisible = true
                        views.buttonSubmit.text = "Nộp bài"
                        views.buttonStart.isVisible = false
                    }
                } else {
                    views.scrollView.isVisible = false
                    views.buttonSubmit.isVisible = false
                    if (viewModel.isLoading.value == false) {
                        resetUIForStart()
                    }
                }
                views.scrollView.post { views.scrollView.fullScroll(ScrollView.FOCUS_UP) }
            }
        }

        viewModel.gradingResult.observe(lifecycleOwner) { result ->
            if (result != null) {
                isShowingGradingResult = true
                views.textViewResult.text =
                    "Kết quả: ${result.score}/${result.total_questions} câu đúng."
                views.textViewResult.isVisible = true
                views.buttonSubmit.text = "Xác nhận"
                views.buttonSubmit.isVisible = true
                views.buttonSubmit.isEnabled = true
                views.buttonStart.isVisible = false
                val correct = result.score
                val total = result.total_questions
                totalCorrectAnswers += correct
                totalQuestionsAnswered += total

                viewModel.gradingResult.observe(lifecycleOwner) { result ->
                    if (result != null) {
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

                        // Hiển thị feedback cho từng câu hỏi
                        result.feedback.forEach { (questionId, feedback) ->
                            val question =
                                viewModel.testQuestions.value?.find { it.id == questionId }
                            val correctAnswerDisplay =
                                question?.options?.get(question.correct_answer)?.let {
                                    "${question.correct_answer.uppercase()}. $it"
                                } ?: question?.correct_answer?.uppercase() ?: "N/A"

                            feedbackViews[questionId]?.apply {
                                isVisible = true
                                if (feedback.status == "correct") {
                                    setTextColor(android.graphics.Color.parseColor("#228B22"))
                                    text = "✅ Trả lời đúng"
                                } else {
                                    setTextColor(android.graphics.Color.parseColor("#B22222"))
                                    text =
                                        "❌ Trả lời sai.\nĐáp án đúng: $correctAnswerDisplay.\nGiải thích: ${feedback.explanation ?: "Không có giải thích."}"
                                }
                            }
                        }

                        views.scrollView.post { views.scrollView.fullScroll(ScrollView.FOCUS_UP) }

                        // Gán hành động nút xác nhận (luôn gán listener)
                        views.buttonSubmit.text = when {
                            isProficiencyTestMode && currentTestCount < maxTests - 1 -> "Bài tiếp theo (${currentTestCount + 2}/5)"
                            isProficiencyTestMode && currentTestCount == maxTests - 1 -> "Xem điểm đánh giá"
                            else -> "Xác nhận"
                        }

                        views.buttonSubmit.setOnClickListener {
                            if (isProficiencyTestMode) {
                                if (currentTestCount < maxTests - 1) {
                                    currentTestCount++
                                    isShowingGradingResult = false
                                    viewModel.clearGradingResult()
                                    startNextProficiencyTest()
                                } else {
                                    isProficiencyTestMode = false
                                    val finalScore = (totalCorrectAnswers * 2).coerceAtMost(100)
                                    saveProficiencyTestResult(finalScore)
                                    confirmGradingResult()
                                }
                            } else {
                                confirmGradingResult()
                            }
                        }
                    } else {
                        views.textViewResult.isVisible = false
                    }
                }
            }
        }

        viewModel.errorMessage.observe(lifecycleOwner) { message ->
            message?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
                if (viewModel.isLoading.value == false) {
                    resetUIForStart()
                }
            }
        }
    }

    private fun saveProficiencyTestResult(score: Int) {
        // TODO: lưu vào Firebase, Room hoặc chia sẻ ViewModel
        Toast.makeText(context, "Điểm đánh giá: $score/100", Toast.LENGTH_LONG).show()
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
        views.customTopicEditTxt?.isVisible = true
        onShowNavigationBar?.invoke()
        views.buttonStart.text = originalButtonText
    }

    private fun resetUIForLoading() {
        views.scrollView.isVisible = true
        views.buttonSubmit.isVisible = false
        views.buttonStart.isVisible = false
        views.customTopicEditTxt?.isVisible = false
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
    fun getRandomTopicFromAssets(context: Context): String {
        val inputStream = context.assets.open("topics.txt")
        val topics = inputStream.bufferedReader().useLines { it.toList() }
        return topics.random()
    }
}