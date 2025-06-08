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
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.example.lingobuddypck.Services.QuestionData
import com.example.lingobuddypck.Services.UserAnswer
import com.example.lingobuddypck.R
import com.example.lingobuddypck.Repository.FirebaseWordRepository
import com.example.lingobuddypck.data.DisplayableQuizContent
import com.example.lingobuddypck.data.QuizDisplayType
import com.example.lingobuddypck.utils.TaskManager
import com.example.lingobuddypck.utils.TopicUtils
import com.example.lingobuddypck.utils.enableSelectableSaveAction

class PassageQuizUIManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: PassageQuizViewModel, // ViewModel sẽ cung cấp DisplayableQuizContent
    private val wordRepository: FirebaseWordRepository, // Giữ lại nếu vẫn dùng trực tiếp
    private val views: PassageQuizViews,
    private val onShowNavigationBar: (() -> Unit)? = null,
    private val onHideNavigationBar: (() -> Unit)? = null
) {

    private var countdownTimer: CountDownTimer? = null
    private val questionViews = mutableMapOf<String, RadioGroup>()
    private val feedbackViews = mutableMapOf<String, TextView>()
    private var isShowingGradingResult = false
    private var originalButtonText: String
    private var currentQuizContent: DisplayableQuizContent? = null // Lưu trữ quiz hiện tại

    init {
        originalButtonText = views.buttonStart.text.toString()
        setupClickListeners()
        setupObservers()
    }

    private fun setupClickListeners() {
        views.buttonStart.setOnClickListener {
            startNewQuizFlow() // Đổi tên hàm
        }

        views.buttonSubmit.setOnClickListener {
            if (isShowingGradingResult) {
                confirmGradingResult()
            } else {
                submitUserAnswers()
            }
        }
    }


    private fun startNewQuizFlow() {
        val topic = views.customTopicEditTxt?.text.toString().trim()
        val isCustom = topic.isNotBlank()
        viewModel.fetchAndPrepareQuiz(
            if (isCustom) topic else TopicUtils.getRandomTopicFromAssets(context),
            isCustom
        )
        resetUIForLoading()
    }

    private fun submitUserAnswers() {
        val userAnswers = mutableListOf<UserAnswer>()
        // Lấy câu hỏi từ currentQuizContent thay vì viewModel.passageQuizData
        val currentQuestions = currentQuizContent?.questions

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
                    allAnswered = false // Tag không đúng hoặc không có
                }
            } else {
                allAnswered = false // Chưa chọn đáp án
            }
        }

        if (allAnswered && userAnswers.size == currentQuestions.size) {
            // Hàm submitAnswers trong ViewModel cũng cần nhận List<QuestionData>
            viewModel.submitAnswers(userAnswers, currentQuestions)
        } else {
            Toast.makeText(context, "Vui lòng trả lời tất cả các câu hỏi.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmGradingResult() {
        isShowingGradingResult = false
        currentQuizContent = null
        views.textViewResult.isVisible = false
        feedbackViews.values.forEach { it.isVisible = false }
        views.buttonSubmit.text ="Nộp bài"
        views.buttonSubmit.isVisible = false
        views.buttonStart.isVisible = true
        views.scrollView.isVisible = false
        views.initialStateContainer.isVisible = true
        views.passageTextView.text = "" // Xóa nội dung đoạn văn
        views.passageTextView.visibility = View.GONE
        views.scrollView.alpha = 1f
        onShowNavigationBar?.invoke()
        viewModel.clearGradingResult()
        viewModel.clearQuizContent()
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
        observeFetchingHint()
        observeDisplayableQuiz()
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
        views.passageTextView.alpha = if (isLoading) 0.1f else 1f
        views.questionsContainer.alpha = if (isLoading) 0.1f else 1f


        if (!isLoading) {
            views.textViewCountdown.isVisible = false // Ẩn countdown khi không loading
            // Nếu có quiz content và không đang hiển thị kết quả -> hiển thị nút submit
            if (currentQuizContent != null && !isShowingGradingResult) {
                views.buttonSubmit.isVisible = true
                views.buttonSubmit.text = "Nộp bài"
                views.buttonStart.isVisible = false
                views.initialStateContainer.isVisible = false
                views.passageTextView.visibility = View.VISIBLE
                views.scrollView.visibility = View.VISIBLE
                onHideNavigationBar?.invoke()
            } else if (viewModel.gradingResult.value == null && viewModel.errorMessage.value != null) {
                // Nếu không có kết quả và có lỗi -> reset về màn hình bắt đầu
                resetUIForStart()
            } else if (currentQuizContent == null && !isShowingGradingResult && viewModel.errorMessage.value == null) {
                // Nếu không có quiz, không có kết quả, không có lỗi -> màn hình bắt đầu
                resetUIForStart()
            }
        } else { // Khi đang loading
            views.buttonSubmit.isVisible = false
            views.buttonStart.isVisible = false
            views.initialStateContainer.isVisible = false
            views.passageTextView.visibility = View.GONE
            views.scrollView.visibility = View.GONE
            onHideNavigationBar?.invoke()
        }
    }

    private fun observeFetchingHint() {
        viewModel.currentLoadingTaskType.observe(lifecycleOwner) { taskType ->
            val fetchingMessages = listOf(
                "⌛ Một chút thôi... AI đang gãi đầu nghĩ đề bài.",
                "🧠 Đang suy nghĩ... đừng rời đi nhé!",
                "🤔 Đang tạo đề bài siêu ngầu...",
                "🌀 Trí tuệ nhân tạo đang uống cà phê...",
                "📚 Đang tham khảo tài liệu cho bạn đây."
            )
            val gradingMessages = listOf(
                "🤖 AI đang chấm điểm như thầy giáo khó tính.",
                "🧘‍♂️ Đang thiền để đánh giá công bằng.",
                "⚖️ Cân đo từng đáp án chính xác.",
                "💡 Đang kiểm tra từng câu trả lời.",
                "📉 Chấm xong sai là tụt mood liền á..."
            )
            // Dựa vào taskType để chọn message phù hợp
            val text = when (taskType) {
                PassageQuizViewModel.LoadingTaskType.FETCHING_QUIZ -> fetchingMessages.random()
                PassageQuizViewModel.LoadingTaskType.GRADING -> gradingMessages.random()
                else -> "Đang xử lý..." // Default hoặc null
            }
            views.textViewLoadingHint.text = text
        }
    }

    private fun observeDisplayableQuiz() {
        viewModel.displayableQuizContent.observe(lifecycleOwner) { quizContent ->
            if (isShowingGradingResult) return@observe

            currentQuizContent = quizContent

            views.questionsContainer.removeAllViews()
            questionViews.clear()
            feedbackViews.clear()

            if (quizContent != null) {
                views.passageTextView.text = HtmlCompat.fromHtml(quizContent.passage, HtmlCompat.FROM_HTML_MODE_LEGACY)
                views.passageTextView.visibility = View.VISIBLE
                renderQuestions(quizContent.questions, quizContent.type) // Truyền cả loại quiz
                views.scrollView.isVisible = true
                views.initialStateContainer.isVisible = false
                onHideNavigationBar?.invoke()

                // Cập nhật UI nút bấm sau khi có dữ liệu quiz
                if (viewModel.isLoading.value == false && !isShowingGradingResult) {
                    views.buttonSubmit.isVisible = true
                    views.buttonSubmit.text = "Nộp bài"
                    views.buttonStart.isVisible = false
                }

            } else {
                // Nếu quizContent là null (ví dụ sau khi confirm kết quả hoặc có lỗi)
                views.passageTextView.text = ""
                views.passageTextView.visibility = View.GONE
                views.scrollView.isVisible = false
                views.buttonSubmit.isVisible = false
                // Nếu không loading và không có lỗi -> reset về màn hình bắt đầu
                if (viewModel.isLoading.value == false && viewModel.errorMessage.value == null) {
                    resetUIForStart()
                }
            }
        }
    }

    // Thêm tham số quizType
    private fun renderQuestions(questions: List<QuestionData>, quizType: QuizDisplayType) {

        questions.forEachIndexed { index, q ->
            addQuestionToLayout(index, q, quizType) // Truyền quizType
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.scroll_bottom_padding)
            )
        }
        views.questionsContainer.addView(spacer)

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
            val percentage = (correct.toFloat() / total * 100).toInt()

            views.textViewResult.text = "Kết quả: $correct/$total câu đúng."
            views.textViewResult.isVisible = true
            views.buttonSubmit.isVisible = true
            views.buttonSubmit.isEnabled = true
            views.buttonStart.isVisible = false
            views.scrollView.isVisible=true
            views.passageTextView.isVisible=true

            feedbackViews.values.forEach { it.isVisible = false }

            if (percentage >= 80) {
                TaskManager.markTaskCompleted(context, TaskManager.TaskType.PASSAGE_QUIZ_SCORE)
            }

            // Check for topic task completion
            val currentTopic = views.customTopicEditTxt?.text?.toString()
            if (currentTopic == TaskManager.getDailyTopic(context)) {
                TaskManager.markTaskCompleted(context, TaskManager.TaskType.PASSAGE_QUIZ_TOPIC)
            }

            result.feedback.forEach { (qid, fb) ->
                val question = currentQuizContent?.questions?.find { it.id == qid }
                val correctAnswerKey = question?.correct_answer
                val correctOptionText = question?.options?.get(correctAnswerKey) ?: ""

                val correctDisplay = if (correctAnswerKey != null) {
                    "${correctAnswerKey.uppercase()}. $correctOptionText"
                } else {
                    "N/A"
                }

                feedbackViews[qid]?.apply {
                    isVisible = true
                    if (fb.status == "correct") {
                        setTextColor(Color.parseColor("#228B22")) // Green
                        text = "✅ Trả lời đúng"
                    } else {
                        setTextColor(Color.parseColor("#B22222")) // Red
                        val explanationText = fb.explanation ?: "Không có giải thích"
                        text =   "❌ Trả lời sai.\nĐáp án đúng: $correctDisplay.\nGiải thích: $explanationText"
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
                viewModel.clearErrorMessage() // Xóa lỗi sau khi hiển thị
                // Nếu không loading và không có quiz nào đang hiển thị -> reset
                if (viewModel.isLoading.value == false && currentQuizContent == null) {
                    resetUIForStart()
                }
            }
        }
    }

    private fun startCountdown() {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(60000, 1000) { // 60 giây
            override fun onTick(millisUntilFinished: Long) {
                views.textViewCountdown.text = "Thời gian chờ: ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                views.textViewCountdown.text = "⏳ Có vẻ hơi lâu... AI đang cố gắng!"
            }
        }.start()
    }

    private fun stopCountdown() {
        countdownTimer?.cancel()
        views.textViewCountdown.isVisible = false // Ẩn khi dừng
    }

    private fun resetUIForStart() {
        isShowingGradingResult = false
        currentQuizContent = null
        views.questionsContainer.removeAllViews()
        questionViews.clear()
        feedbackViews.clear()
        views.passageTextView.text = ""
        views.passageTextView.visibility = View.GONE
        views.scrollView.isVisible = false
        views.buttonSubmit.isVisible = false
        views.textViewResult.isVisible = false
        views.progressBar.isVisible = false
        views.textViewLoadingHint.isVisible = false
        views.textViewCountdown.isVisible = false
        views.aiAvatar.isVisible = false
        views.passageTextView.alpha = 1f
        views.questionsContainer.alpha = 1f
        views.buttonStart.isVisible = true
        views.buttonStart.isEnabled = true
        views.initialStateContainer.isVisible = true
        views.recyclerView?.isVisible = true
        views.customTopicEditTxt?.isVisible = true
        views.customTopicEditTxt?.text?.clear()
        onShowNavigationBar?.invoke()
        views.buttonStart.text = originalButtonText
    }

    private fun resetUIForLoading() {
        isShowingGradingResult = false
        currentQuizContent = null
        views.passageTextView.text = ""
        views.passageTextView.visibility = View.GONE
        views.questionsContainer.removeAllViews()
        questionViews.clear()
        feedbackViews.clear()
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

    // Thêm tham số quizType
    private fun addQuestionToLayout(index: Int, questionData: QuestionData, quizType: QuizDisplayType) {
        val inflater = LayoutInflater.from(context)
        val questionView = inflater.inflate(R.layout.item_test_question, views.questionsContainer, false)

        val tvQuestionNumber = questionView.findViewById<TextView>(R.id.textViewQuestionNumber)
        val tvQuestionContent = questionView.findViewById<TextView>(R.id.textViewQuestionContent)
        val rgOptions = questionView.findViewById<RadioGroup>(R.id.radioGroupOptions)
        val tvFeedback = questionView.findViewById<TextView>(R.id.textViewFeedback)
        val blankIndex=index+1
        // Xử lý hiển thị dựa trên quizType
        when (quizType) {
            QuizDisplayType.FILL_THE_BLANK -> {
                tvQuestionNumber.text = "Blank $blankIndex:"
                tvQuestionContent.visibility = View.GONE // Ẩn nội dung câu hỏi
            }
            QuizDisplayType.READING_COMPREHENSION -> {
                tvQuestionNumber.text = "Question $blankIndex:"
                tvQuestionContent.text = questionData.question_text // Hiển thị câu hỏi đọc hiểu
                tvQuestionContent.visibility = View.VISIBLE
            }
        }

        // Sắp xếp và thêm các lựa chọn
        val sortedOptions = questionData.options.entries.sortedBy { it.key }
        sortedOptions.forEach { (optionKey, optionText) ->
            val radioButton = RadioButton(context) // Nên dùng style từ theme nếu có
            radioButton.text = "${optionKey.uppercase()}. $optionText"
            radioButton.tag = optionKey
            radioButton.id = View.generateViewId()
            rgOptions.addView(radioButton)
        }
        views.questionsContainer.addView(questionView)
        questionViews[questionData.id] = rgOptions
        feedbackViews[questionData.id] = tvFeedback
        tvFeedback.isVisible = false // Mặc định ẩn feedback
    }
}
