package com.example.lingobuddypck

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
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
    private lateinit var buttonSubmit: Button
    private lateinit var buttonStart: Button
    private lateinit var textViewResult: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var customTopicEditTxt: EditText
    private val wordRepository = FirebaseWordRepository()

    // Lưu trữ view theo ID câu hỏi
    private val questionViews = mutableMapOf<String, RadioGroup>()
    private val feedbackViews = mutableMapOf<String, TextView>() // NEW: hiển thị phản hồi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        progressBar = findViewById(R.id.progressBarTest)
        questionsContainer = findViewById(R.id.questionsContainerLayout)
        buttonSubmit = findViewById(R.id.buttonSubmitTest)
        buttonStart = findViewById(R.id.buttonStartTest)
        textViewResult = findViewById(R.id.textViewTestResult)
        scrollView = findViewById(R.id.scrollViewTest)
        customTopicEditTxt = findViewById(R.id.editTextCustomTopic)

        setupObservers()

        buttonStart.setOnClickListener {
            val isCustom:Boolean
            val topic:String
            if (customTopicEditTxt.text.toString().isBlank()) {
                isCustom=false;
                topic=getRandomTopicFromAssets(this)
            }
            else {
                topic= customTopicEditTxt.text.toString()
                isCustom=true;
            }

            viewModel.fetchTest(topic,isCustom)
            it.visibility = View.GONE
            textViewResult.visibility = View.GONE
            customTopicEditTxt.visibility = View.GONE
        }

        buttonSubmit.setOnClickListener {
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
                Toast.makeText(this, "Vui lòng trả lời tất cả các câu hỏi.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.isVisible = isLoading
            buttonStart.isEnabled = !isLoading
            buttonSubmit.isEnabled = !isLoading && (viewModel.testQuestions.value != null && viewModel.gradingResult.value == null)
        }

        viewModel.testQuestions.observe(this) { questions ->
            questionsContainer.removeAllViews()
            questionViews.clear()
            feedbackViews.clear()

            if (questions != null) {
                scrollView.visibility = View.VISIBLE
                buttonSubmit.visibility = View.VISIBLE
                questions.forEachIndexed { index, questionData ->
                    addQuestionToLayout(index, questionData)
                }
            } else {
                scrollView.visibility = View.GONE
                buttonSubmit.visibility = View.GONE
                if (viewModel.isLoading.value == false) {
                    buttonStart.visibility = View.VISIBLE
                }
            }
        }

        viewModel.gradingResult.observe(this) { result ->
            if (result != null) {
                textViewResult.text = "Kết quả: ${result.score}/${result.total_questions} câu đúng."
                textViewResult.visibility = View.VISIBLE
                buttonSubmit.isEnabled = false
                buttonStart.text = "Làm lại bài test"
                buttonStart.visibility = View.VISIBLE
                customTopicEditTxt.visibility=View.VISIBLE

                // Hiển thị feedback
                result.feedback?.forEach { (questionId, status) ->
                    val feedbackTextView = feedbackViews[questionId]
                    val correctAnswer = viewModel.testQuestions.value
                        ?.find { it.id == questionId }
                        ?.correct_answer
                        ?.uppercase()

                    feedbackTextView?.apply {
                        visibility = View.VISIBLE
                        if (status.status == "correct") {
                            setTextColor(Color.parseColor("#228B22"))
                            text = "✅ Trả lời đúng"
                        } else {
                            setTextColor(Color.parseColor("#B22222"))
                            text = "❌ Trả lời sai. Đáp án đúng: $correctAnswer. Giải thích: ${status.explanation}"
                        }
                    }
                }
            } else {
                textViewResult.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
                if (viewModel.testQuestions.value == null) {
                    buttonStart.visibility = View.VISIBLE
                    scrollView.visibility = View.GONE
                    buttonSubmit.visibility = View.GONE
                }
            }
        }
    }

    fun getRandomTopicFromAssets(context: Context): String {
        val inputStream = context.assets.open("topics.txt")
        val topics = inputStream.bufferedReader().useLines { it.toList() }
        return topics.random()
    }

    private fun addQuestionToLayout(index: Int, questionData: QuestionData) {
        val inflater = LayoutInflater.from(this)
        val questionView = inflater.inflate(R.layout.item_test_question, questionsContainer, false)

        val tvQuestionNumber = questionView.findViewById<TextView>(R.id.textViewQuestionNumber)
        val tvQuestionContent = questionView.findViewById<TextView>(R.id.textViewQuestionContent)
        val rgOptions = questionView.findViewById<RadioGroup>(R.id.radioGroupOptions)
        val tvFeedback = questionView.findViewById<TextView>(R.id.textViewFeedback)

        tvQuestionNumber.text = "Câu ${index + 1}:"
        tvQuestionContent.text = questionData.question_text
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
            radioButton.tag = optionKey
            radioButton.id = View.generateViewId()
            rgOptions.addView(radioButton)
        }

        questionsContainer.addView(questionView)
        questionViews[questionData.id] = rgOptions
        feedbackViews[questionData.id] = tvFeedback // <-- lưu feedback view theo ID câu hỏi
    }
}
