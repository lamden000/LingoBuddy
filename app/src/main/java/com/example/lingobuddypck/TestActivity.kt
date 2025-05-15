package com.example.lingobuddypck

import android.content.Context
import android.graphics.Color
import android.os.Bundle
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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.lingobuddypck.Network.TogetherAI.QuestionData
import com.example.lingobuddypck.Network.TogetherAI.UserAnswer
import com.example.lingobuddypck.ViewModel.Repository.FirebaseWordRepository
import com.example.lingobuddypck.ViewModel.TestViewModel
import com.example.lingobuddypck.ui.utils.enableSelectableSaveAction
class TestActivity : AppCompatActivity() {

    private val viewModel: TestViewModel by viewModels()

    private lateinit var progressBar: ProgressBar
    private lateinit var questionsContainer: LinearLayout
    private lateinit var buttonSubmit: Button // Used for Submit and Xác nhận
    private lateinit var buttonStart: Button // Used for Start and Làm lại bài test
    private lateinit var textViewResult: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var customTopicEditTxt: EditText
    private lateinit var textViewLoadingHint: TextView
    private lateinit var textViewCountdown: TextView
    private lateinit var aiAvatar: ImageView
    private var countdownTimer: CountDownTimer? = null
    private val wordRepository = FirebaseWordRepository()

    // Lưu trữ view theo ID câu hỏi
    private val questionViews = mutableMapOf<String, RadioGroup>()
    private val feedbackViews = mutableMapOf<String, TextView>() // NEW: hiển thị phản hồi

    // NEW: State variable to manage if we are in the "show result and confirm" state
    private var isShowingGradingResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        aiAvatar=findViewById(R.id.aiAvatarLoading)
        textViewCountdown = findViewById(R.id.textViewCountdown)
        textViewLoadingHint=findViewById(R.id.textViewLoadingHint)
        progressBar = findViewById(R.id.progressBarTest)
        questionsContainer = findViewById(R.id.questionsContainerLayout)
        buttonSubmit = findViewById(R.id.buttonSubmitTest)
        buttonStart = findViewById(R.id.buttonStartTest)
        textViewResult = findViewById(R.id.textViewTestResult)
        scrollView = findViewById(R.id.scrollViewTest)
        customTopicEditTxt = findViewById(R.id.editTextCustomTopic)

        setupObservers()

        buttonStart.setOnClickListener {
            // This listener is now ONLY for starting a *new* test (initial or after confirmation)
            startNewTestFlow()
        }

        buttonSubmit.setOnClickListener {
            // This listener now handles BOTH submitting answers and confirming the result
            if (isShowingGradingResult) {
                // If we are in the result state, this click means "Xác nhận"
                confirmGradingResult()
            } else {
                // Otherwise, it's the standard submit answers action
                submitUserAnswers()
            }
        }

        // Initial UI state
        resetUIForStart()
    }

    // Helper function for the "Start Test" button click
    private fun startNewTestFlow() {
        val isCustom: Boolean
        val topic: String
        if (customTopicEditTxt.text.toString().isBlank()) {
            isCustom = false
            topic = getRandomTopicFromAssets(this) // Assume this function exists
        } else {
            topic = customTopicEditTxt.text.toString()
            isCustom = true
        }

        viewModel.fetchTest(topic, isCustom)
        resetUIForLoading() // Or handle in observer
    }

    // Helper function for the "Submit Answers" button click (when not confirming)
    private fun submitUserAnswers() {
        val userAnswers = mutableListOf<UserAnswer>()
        var allAnswered = true

        // Collect answers
        questionViews.forEach { (questionId, radioGroup) ->
            val selectedRadioButtonId = radioGroup.checkedRadioButtonId
            if (selectedRadioButtonId != -1) {
                val selectedRadioButton = radioGroup.findViewById<RadioButton>(selectedRadioButtonId)
                val answerKey = selectedRadioButton.tag as? String
                if (answerKey != null) {
                    userAnswers.add(UserAnswer(questionId, answerKey))
                } else {
                    // This case shouldn't happen if tag is set correctly, but good check
                    allAnswered = false
                }
            } else {
                allAnswered = false
            }
        }

        // Check if all questions were answered (and the number matches)
        if (allAnswered && userAnswers.size == viewModel.testQuestions.value?.size) {
            viewModel.submitAnswers(userAnswers)
        } else {
            Toast.makeText(this, "Vui lòng trả lời tất cả các câu hỏi.", Toast.LENGTH_SHORT).show()
        }
    }

    // NEW: Helper function for the "Xác nhận" button click
    private fun confirmGradingResult() {
        isShowingGradingResult = false // Exit the result state

        // Hide result and feedback elements
        textViewResult.visibility = View.GONE
        feedbackViews.values.forEach { it.visibility = View.GONE } // Hide all feedback views
        // Clear feedback map if you want to free up memory, though not strictly necessary here
        // feedbackViews.clear()

        // Reset buttonSubmit for the next test cycle
        buttonSubmit.text = "Nộp bài" // Reset text
        buttonSubmit.visibility = View.GONE // Hide it until new questions load

        // Show the "Làm lại bài test" button
        buttonStart.text = "Làm lại bài test" // Ensure text is correct
        buttonStart.visibility = View.VISIBLE
        scrollView.alpha= 0.1F
        customTopicEditTxt.visibility = View.VISIBLE // Show topic edit again

        // Clear the grading result in the ViewModel
        viewModel.clearGradingResult()
        // Note: Clearing gradingResult might re-trigger the observer briefly,
        // but setting isShowingGradingResult to false first prevents entering the result state again immediately.
    }


    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.isVisible = isLoading
            textViewLoadingHint.isVisible = isLoading
            textViewCountdown.isVisible = isLoading
            buttonStart.isEnabled = !isLoading
            buttonSubmit.isEnabled = !isLoading

            if (isLoading) {
                val loadingMessages = listOf(
                    "⌛ Một chút thôi... AI đang gãi đầu nghĩ câu hỏi.",
                    "🧠 Đang suy nghĩ... đừng rời đi nhé!",
                    "🤔 Đang tạo đề bài siêu ngầu cho bạn...",
                    "🌀 Trí tuệ nhân tạo đang uống cà phê...",
                    "📚 Đang tham khảo vài triệu tài liệu. Bình tĩnh."
                )
                scrollView.alpha= 0.1F
                textViewLoadingHint.text = loadingMessages.random()
                aiAvatar.visibility=View.VISIBLE
                countdownTimer?.cancel()
                countdownTimer = object : CountDownTimer(60000, 1000) { // 60 seconds timer
                    override fun onTick(millisUntilFinished: Long) {
                        val secondsLeft = millisUntilFinished / 1000
                        textViewCountdown.text = "Thời gian còn lại: ${secondsLeft}s"
                    }

                    override fun onFinish() {
                        textViewCountdown.text = "⏳ Có vẻ đang mất nhiều thời gian... hãy kiên nhẫn!"
                    }
                }.start()
            } else {
                // Stop loading UI
                countdownTimer?.cancel()
                textViewCountdown.visibility = View.GONE
                aiAvatar.visibility=View.GONE
                scrollView.alpha= 1F

                if (viewModel.testQuestions.value != null && !isShowingGradingResult) {
                    buttonSubmit.visibility = View.VISIBLE
                    buttonSubmit.text = "Nộp bài"
                    buttonStart.visibility = View.GONE
                    customTopicEditTxt.visibility=View.GONE
                } else if (viewModel.gradingResult.value == null && viewModel.errorMessage.value != null) {
                    resetUIForStart()
                }
            }
        }

        viewModel.testQuestions.observe(this) { questions ->
            if (!isShowingGradingResult) {
                questionsContainer.removeAllViews()
                questionViews.clear()
                feedbackViews.clear()

                if (questions != null && questions.isNotEmpty()) {
                    scrollView.visibility = View.VISIBLE
                    customTopicEditTxt.visibility = View.GONE

                    questions.forEachIndexed { index, questionData ->
                        addQuestionToLayout(index, questionData)
                    }
                    if (viewModel.isLoading.value == false) {
                        buttonSubmit.visibility = View.VISIBLE
                        buttonSubmit.text = "Nộp bài"
                        buttonStart.visibility = View.GONE
                    }

                } else {
                    // No questions loaded (initial or error)
                    scrollView.visibility = View.GONE
                    buttonSubmit.visibility = View.GONE
                    if (viewModel.isLoading.value == false) {
                        resetUIForStart() // Show start button and topic input
                    }
                }
            }
            // If isShowingGradingResult is true, this observer update is ignored
            // because the UI is showing the result confirmation state.
        }

        // UPDATED: Grading result observer to handle the "Xác nhận" state
        viewModel.gradingResult.observe(this) { result ->
            if (result != null) {
                isShowingGradingResult = true // Enter the result state

                // Display the result
                textViewResult.text = "Kết quả: ${result.score}/${result.total_questions} câu đúng."
                textViewResult.visibility = View.VISIBLE

                // Show and update the submit button to be "Xác nhận"
                buttonSubmit.text = "Xác nhận"
                buttonSubmit.visibility = View.VISIBLE
                buttonSubmit.isEnabled = true // Make sure it's enabled for confirming

                // Hide the start button
                buttonStart.visibility = View.GONE

                // Display feedback for each question
                result.feedback.forEach { (questionId, feedback) ->
                    // Find the correct answer text from the original questions
                    val question = viewModel.testQuestions.value?.find { it.id == questionId }
                    val correctAnswerDisplay = question?.options?.get(question.correct_answer)?.let {
                        "${question.correct_answer.uppercase()}. $it"
                    } ?: question?.correct_answer?.uppercase() ?: "N/A"


                    val feedbackTextView = feedbackViews[questionId]
                    feedbackTextView?.apply {
                        visibility = View.VISIBLE
                        if (feedback.status == "correct") {
                            setTextColor(Color.parseColor("#228B22")) // Green
                            text = "✅ Trả lời đúng"
                        } else {
                            setTextColor(Color.parseColor("#B22222")) // Red
                            text = "❌ Trả lời sai.\nĐáp án đúng: $correctAnswerDisplay.\nGiải thích: ${feedback.explanation ?: "Không có giải thích."}"
                            // Use ?: "Không có giải thích" in case AI doesn't provide explanation for some reason
                        }
                    }
                }
                // After showing feedback, scroll to the top of the feedback section or result
                // You might want to scroll to textViewResult or the first feedbackView
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_UP) }


            } else {
                textViewResult.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage() // Assume you have this to consume the message
                // After an error and loading stops, return to the start state
                if (viewModel.isLoading.value == false) {
                    resetUIForStart()
                }
            }
        }
    }

    // Helper function to reset UI to the initial start state
    private fun resetUIForStart() {
        questionsContainer.removeAllViews()
        questionViews.clear()
        feedbackViews.clear()
        scrollView.visibility = View.GONE
        buttonSubmit.visibility = View.GONE // Hide submit initially
        textViewResult.visibility = View.GONE
        textViewLoadingHint.visibility = View.GONE // Hide loading hint
        textViewCountdown.visibility = View.GONE // Hide countdown
        aiAvatar.visibility = View.GONE // Hide AI avatar
        scrollView.alpha= 1F
        buttonStart.text = "Bắt đầu làm bài" // Reset start button text
        buttonStart.visibility = View.VISIBLE
        customTopicEditTxt.visibility = View.VISIBLE // Show topic input
        customTopicEditTxt.text.clear() // Clear previous topic
    }

    // Helper function to reset UI to the loading state (partially redundant with observer, but good for clarity)
    private fun resetUIForLoading() {
        scrollView.visibility = View.VISIBLE // Keep scrollview visible but dim it
        buttonSubmit.visibility = View.GONE // Hide submit while loading
        buttonStart.visibility = View.GONE // Hide start while loading
        customTopicEditTxt.visibility = View.GONE // Hide topic input while loading
        textViewResult.visibility = View.GONE // Hide result while loading
        feedbackViews.values.forEach { it.visibility = View.GONE } // Hide feedback while loading
    }


    fun getRandomTopicFromAssets(context: Context): String {

        val inputStream = context.assets.open("topics.txt")

        val topics = inputStream.bufferedReader().useLines { it.toList() }

        return topics.random()

    }

    private fun addQuestionToLayout(index: Int, questionData: QuestionData) { // Changed QuestionData to TestQuestion
        val inflater = LayoutInflater.from(this)
        val questionView = inflater.inflate(R.layout.item_test_question, questionsContainer, false)

        val tvQuestionNumber = questionView.findViewById<TextView>(R.id.textViewQuestionNumber)
        val tvQuestionContent = questionView.findViewById<TextView>(R.id.textViewQuestionContent)
        val rgOptions = questionView.findViewById<RadioGroup>(R.id.radioGroupOptions)
        val tvFeedback = questionView.findViewById<TextView>(R.id.textViewFeedback) // Assuming item_test_question has this TextView

        tvQuestionNumber.text = "Câu ${index + 1}:"
        tvQuestionContent.text = questionData.question_text
        // Assuming enableSelectableSaveAction is an extension function you have
        tvQuestionContent.enableSelectableSaveAction(this) { selectedText, note ->
            wordRepository.saveWord(
                word = selectedText,
                note = note,
                onSuccess = { Toast.makeText(this, "Đã lưu vào từ điển của bạn.", Toast.LENGTH_SHORT).show() },
                onFailure = { e -> Toast.makeText(this, "Lỗi khi lưu: ${e.message}", Toast.LENGTH_SHORT).show() }
            )
        }

        val sortedOptions = questionData.options.entries.sortedBy { it.key }
        sortedOptions.forEach { (optionKey, optionText) ->
            val radioButton = RadioButton(this)
            radioButton.text = "${optionKey.uppercase()}. $optionText"
            radioButton.tag = optionKey // Store the option key
            radioButton.id = View.generateViewId() // Generate a unique ID
            rgOptions.addView(radioButton)
        }

        questionsContainer.addView(questionView)
        questionViews[questionData.id] = rgOptions // Store RadioGroup by question ID
        feedbackViews[questionData.id] = tvFeedback // Store Feedback TextView by question ID
    }
}