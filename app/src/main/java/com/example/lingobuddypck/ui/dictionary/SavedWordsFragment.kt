package com.example.lingobuddypck.ui.dictionary
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.NavigationActivity
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.Network.TogetherAI.QuestionData
import com.example.lingobuddypck.Network.TogetherAI.UserAnswer
import com.example.lingobuddypck.R
import com.example.lingobuddypck.ViewModel.Repository.AiQuizService
import com.example.lingobuddypck.ViewModel.Repository.FirebaseWordRepository
import com.example.lingobuddypck.ViewModel.Repository.SavedWord
import com.example.lingobuddypck.ViewModel.TestViewModel
import com.example.lingobuddypck.adapter.SavedWordsAdapter
import com.example.lingobuddypck.ui.utils.enableSelectableSaveAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder // Hoặc AlertDialog thông thường
import com.google.gson.Gson

class SavedWordsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewNoWords: TextView
    private lateinit var wordsAdapter: SavedWordsAdapter
    private lateinit var aiAvatar: ImageView
    private lateinit var questionsContainer: LinearLayout
    private lateinit var buttonSubmit: Button
    private lateinit var buttonStart: Button
    private lateinit var textViewResult: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var textViewLoadingHint: TextView
    private lateinit var textViewCountdown: TextView
    private val questionViews = mutableMapOf<String, RadioGroup>()
    private val feedbackViews = mutableMapOf<String, TextView>()
    private var isShowingGradingResult = false
    private var countdownTimer: CountDownTimer? = null

    private val wordRepository = FirebaseWordRepository()

    private val aiQuizService: AiQuizService by lazy {
        AiQuizService(Gson(), RetrofitClient.instance)
    }

    private val viewModel: SavedWordsViewModel by viewModels {
        SavedWordsViewModel.Factory(aiQuizService)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_saved_words, container, false)

        aiAvatar=view.findViewById(R.id.aiAvatarLoading)
        textViewCountdown = view.findViewById(R.id.textViewCountdown)
        textViewLoadingHint=view.findViewById(R.id.textViewLoadingHint)
        questionsContainer = view.findViewById(R.id.questionsContainerLayout)
        buttonSubmit = view.findViewById(R.id.buttonSubmitTest)
        buttonStart = view.findViewById(R.id.buttonCreateQuiz)
        textViewResult = view.findViewById(R.id.textViewTestResult)
        scrollView = view.findViewById(R.id.scrollViewTest)
        recyclerView = view.findViewById(R.id.recyclerViewSavedWords)
        progressBar = view.findViewById(R.id.progressBarSavedWords)
        textViewNoWords = view.findViewById(R.id.textViewNoWords)

        setupObservers()

        buttonStart.setOnClickListener {
            showConfirmationMakeTestDiaglog()
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

        setupRecyclerView()
        observeViewModel()

        return view
    }

    private fun startNewTestFlow() {
        viewModel.fetchTest()
        resetUIForLoading()
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
            Toast.makeText(requireContext(), "Vui lòng trả lời tất cả các câu hỏi.", Toast.LENGTH_SHORT).show()
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
        buttonStart.text = "Ôn tập" // Ensure text is correct
        buttonStart.visibility = View.VISIBLE
        scrollView.visibility=View.GONE
        recyclerView.visibility=View.VISIBLE
        (activity as? NavigationActivity)?.showBottomNavigationBar()
        // Clear the grading result in the ViewModel
        viewModel.clearGradingResult()
        // Note: Clearing gradingResult might re-trigger the observer briefly,
        // but setting isShowingGradingResult to false first prevents entering the result state again immediately.
    }


    private fun setupObservers() {
        viewModel.isLoading.observe(requireActivity()) { isLoading ->
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
                } else if (viewModel.gradingResult.value == null && viewModel.errorMessage.value != null) {
                    resetUIForStart()
                }
            }
        }

        viewModel.testQuestions.observe(requireActivity()) { questions ->
            if (!isShowingGradingResult) {
                questionsContainer.removeAllViews()
                questionViews.clear()
                feedbackViews.clear()

                if (questions != null && questions.isNotEmpty()) {
                    scrollView.visibility = View.VISIBLE

                    questions.forEachIndexed { index, questionData ->
                        addQuestionToLayout(index, questionData)
                    }
                    val bottomSpacer = View(requireActivity()).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            resources.getDimensionPixelSize(R.dimen.scroll_bottom_padding) // Hoặc 100.dp.toPx() nếu bạn có extension
                        )
                    }
                    questionsContainer.addView(bottomSpacer)
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
        viewModel.gradingResult.observe(requireActivity()) { result ->
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

        viewModel.errorMessage.observe(requireActivity()) { message ->
            message?.let {
                Toast.makeText(requireActivity(), it, Toast.LENGTH_LONG).show()
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
        buttonStart.text = "Ôn tập" // Reset start button text
        buttonStart.visibility = View.VISIBLE
    }

    // Helper function to reset UI to the loading state (partially redundant with observer, but good for clarity)
    private fun resetUIForLoading() {
        scrollView.visibility = View.VISIBLE // Keep scrollview visible but dim it
        buttonSubmit.visibility = View.GONE // Hide submit while loading
        buttonStart.visibility = View.GONE // Hide start while loading
        textViewResult.visibility = View.GONE // Hide result while loading
        feedbackViews.values.forEach { it.visibility = View.GONE } // Hide feedback while loading
        recyclerView.visibility=View.GONE
        (activity as? NavigationActivity)?.hideBottomNavigationBar()
    }


    private fun addQuestionToLayout(index: Int, questionData: QuestionData) { // Changed QuestionData to TestQuestion
        val inflater = LayoutInflater.from(requireContext())
        val questionView = inflater.inflate(R.layout.item_test_question, questionsContainer, false)

        val tvQuestionNumber = questionView.findViewById<TextView>(R.id.textViewQuestionNumber)
        val tvQuestionContent = questionView.findViewById<TextView>(R.id.textViewQuestionContent)
        val rgOptions = questionView.findViewById<RadioGroup>(R.id.radioGroupOptions)
        val tvFeedback = questionView.findViewById<TextView>(R.id.textViewFeedback) // Assuming item_test_question has this TextView

        tvQuestionNumber.text = "Câu ${index + 1}:"
        tvQuestionContent.text = questionData.question_text
        // Assuming enableSelectableSaveAction is an extension function you have
        tvQuestionContent.enableSelectableSaveAction(requireContext()) { selectedText, note ->
            wordRepository.saveWord(
                word = selectedText,
                note = note,
                onSuccess = { Toast.makeText(requireContext(), "Đã lưu vào từ điển của bạn.", Toast.LENGTH_SHORT).show() },
            )
        }

        val sortedOptions = questionData.options.entries.sortedBy { it.key }
        sortedOptions.forEach { (optionKey, optionText) ->
            val radioButton = RadioButton(requireContext())
            radioButton.text = "${optionKey.uppercase()}. $optionText"
            radioButton.tag = optionKey // Store the option key
            radioButton.id = View.generateViewId() // Generate a unique ID
            rgOptions.addView(radioButton)
        }

        questionsContainer.addView(questionView)
        questionViews[questionData.id] = rgOptions // Store RadioGroup by question ID
        feedbackViews[questionData.id] = tvFeedback // Store Feedback TextView by question ID
    }

    private fun setupRecyclerView() {
        wordsAdapter = SavedWordsAdapter(
            onEditClick = { savedWord ->
                showEditWordDialog(savedWord)
            },
            onDeleteClick = { savedWord ->
                showDeleteConfirmationDialog(savedWord)
            }
        )
        recyclerView.apply {
            adapter = wordsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.savedWords.observe(viewLifecycleOwner) { words ->
            if (words.isNullOrEmpty()) {
                textViewNoWords.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                textViewNoWords.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                wordsAdapter.submitList(words)
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { errorMessage ->
                if (errorMessage != null) {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
        viewModel.operationResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showConfirmationMakeTestDiaglog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bắt đầu bài kiểm tra")
            .setMessage("Bạn đã sẵn sàng làm bài kiểm tra chưa?")
            .setPositiveButton("Rồi") { dialog, _ ->
                startNewTestFlow()
                dialog.dismiss()
            }
            .setNegativeButton("Chưa") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showDeleteConfirmationDialog(savedWord: SavedWord) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xóa từ")
            .setMessage("Bạn chắc chắn muốn xóa từ này chứ '${savedWord.word}'?")
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Xóa") { dialog, _ ->
                viewModel.deleteWord(savedWord.id)
                dialog.dismiss()
            }
            .show()
    }

    private fun showEditWordDialog(savedWord: SavedWord) {
        // Tạo layout cho dialog (ví dụ: dialog_edit_word.xml)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_word, null)
        val editTextWord = dialogView.findViewById<EditText>(R.id.editTextDialogWord) // Cần có ID này trong dialog_edit_word.xml
        val editTextNote = dialogView.findViewById<EditText>(R.id.editTextDialogNote) // Cần có ID này trong dialog_edit_word.xml

        editTextWord.setText(savedWord.word)
        editTextNote.setText(savedWord.note)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Word")
            .setView(dialogView)
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Save") { dialog, _ ->
                val updatedWord = editTextWord.text.toString().trim()
                val updatedNote = editTextNote.text.toString().trim()
                viewModel.updateWord(savedWord.id, updatedWord, updatedNote)
                dialog.dismiss()
            }
            .show()
    }

}