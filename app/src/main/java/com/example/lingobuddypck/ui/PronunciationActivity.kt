package com.example.lingobuddypck.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.lingobuddypck.Services.PronunciationAiService
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.R
import com.example.lingobuddypck.Services.PronunciationFeedback
import com.example.lingobuddypck.ViewModel.PronunciationViewModel
import com.example.lingobuddypck.utils.TopicUtils
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.example.lingobuddypck.utils.TaskManager

class PronunciationActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var btnStart: Button
    private lateinit var btnGenerateReference: Button
    private lateinit var etTopicInput: TextInputEditText
    private lateinit var txtResult: TextView
    private lateinit var tvReference: TextView
    private lateinit var txtStatus: TextView
    private lateinit var progressBar: ProgressBar

    private val viewModel: PronunciationViewModel by viewModels {
        PronunciationViewModel.Factory(
            PronunciationAiService(RetrofitClient.instance, Gson())
        )
    }

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pronunciation)

        btnStart = findViewById(R.id.btnStart)
        btnGenerateReference = findViewById(R.id.btnGenerateReference)
        etTopicInput = findViewById(R.id.etTopicInput)
        txtResult = findViewById(R.id.txtResult)
        tvReference = findViewById(R.id.tvReferenceText)
        txtStatus = findViewById(R.id.txtStatus)
        progressBar = findViewById(R.id.progressBar)

        // Get topic from intent if available
        intent.getStringExtra("topic")?.let { topic ->
            etTopicInput.setText(topic)
            // Automatically generate reference text for the topic
            viewModel.generateNewReferenceText(topic, true)
        }

        checkAudioPermission()
        setupListeners()
        setupObservers()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(createRecognitionListener())
    }

    private fun setupListeners() {
        btnStart.setOnClickListener {
            startListening()
        }

        btnGenerateReference.setOnClickListener {
            btnGenerateReference.isEnabled = false
            if (!etTopicInput.text.isNullOrEmpty()) {
                val topic = etTopicInput.text.toString().trim()
                viewModel.generateNewReferenceText(topic, true)
            } else {
                viewModel.generateNewReferenceText(TopicUtils.getRandomTopicFromAssets(this), false)
            }
        }
    }

    private fun setupObservers() {
        viewModel.referenceText.observe(this) { text ->
            if(text!="Hello, How are you today?") {
                val originalText = btnGenerateReference.text.toString()
                val cooldownSeconds = 5
                object : CountDownTimer((cooldownSeconds * 1000).toLong(), 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val secondsRemaining = millisUntilFinished / 1000
                        btnGenerateReference.text = "Đợi ${secondsRemaining}s"
                    }

                    override fun onFinish() {
                        btnGenerateReference.text = originalText
                        btnGenerateReference.isEnabled = true
                    }
                }.start()
                Handler(Looper.getMainLooper()).postDelayed({
                    btnGenerateReference.isEnabled = true
                }, 5000)
            }
            tvReference.text = text
            txtResult.text = ""
            viewModel.clearPronunciationFeedback()
        }

        viewModel.userSpeechResult.observe(this) { result ->
            txtResult.text = "Bạn đã nói: $result"
            viewModel.checkPronunciation(result, viewModel.referenceText.value ?: "")
        }

        viewModel.pronunciationFeedback.observe(this) { feedback ->
            feedback?.let {
                showCorrectionResult(it)
            }
        }

        viewModel.statusMessage.observe(this) { message ->
            txtStatus.text = message
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.setErrorMessage(null) // Consume the error by setting to null
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnStart.isEnabled = !isLoading
            etTopicInput.isEnabled = !isLoading
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                Toast.makeText(this, "Quyền ghi âm bị từ chối. Không thể sử dụng chức năng này.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Cần quyền ghi âm để sử dụng tính năng này.", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.packageName)

        try {
            speechRecognizer.startListening(intent)
            // CORRECTED: Call ViewModel function to update status
            viewModel.updateStatusMessage("Đang nghe...")
        } catch (e: Exception) {
            Log.e("PronunciationActivity", "Error starting speech recognition: ${e.message}")
            // CORRECTED: Call ViewModel function to set error
            viewModel.setErrorMessage("Lỗi khi bắt đầu nhận diện giọng nói: ${e.message}")
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val userSpeech = matches[0]
                    viewModel.setUserSpeechResult(userSpeech)
                }
                // CORRECTED: Call ViewModel function to update status
                viewModel.updateStatusMessage("Ghi âm kết thúc.")
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NETWORK -> "Lỗi mạng: Không kết nối được."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nhận diện được giọng nói."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Thiếu quyền RECORD_AUDIO."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Không nhận diện được lời nói."
                    SpeechRecognizer.ERROR_CLIENT -> "Lỗi client nhận diện giọng nói."
                    else -> "Lỗi không xác định: $error"
                }
                Log.e("SpeechRecognizer", errorMessage)
                // CORRECTED: Call ViewModel function to set error
                viewModel.setErrorMessage(errorMessage)
                // CORRECTED: Call ViewModel function to update status
                viewModel.updateStatusMessage("Sẵn sàng.")
            }

            override fun onReadyForSpeech(params: Bundle?) {
                // CORRECTED: Call ViewModel function to update status
                viewModel.updateStatusMessage("Đang nghe...")
            }

            override fun onBeginningOfSpeech() {
                // CORRECTED: Call ViewModel function to update status
                viewModel.updateStatusMessage("Đã nhận diện giọng nói...")
            }

            override fun onEndOfSpeech() {
                // CORRECTED: Call ViewModel function to update status
                viewModel.updateStatusMessage("Đang xử lý...")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun showCorrectionResult(feedback: PronunciationFeedback) {
        val mistakesText = if (feedback.mistakes.isNotEmpty()) feedback.mistakes.joinToString(", ") else "Không có lỗi"
        val suggestionsText = if (feedback.suggestions.isNotEmpty()) feedback.suggestions.joinToString("\n") else "Không có gợi ý sửa lỗi."

        // Check for task completion
        if (feedback.score >= 8) {
            TaskManager.markTaskCompleted(this, TaskManager.TaskType.PRONUNCIATION_SCORE)
        }

        val currentTopic = TaskManager.getDailyTopic(this)
        if (tvReference.text.toString().contains(currentTopic, ignoreCase = true)) {
            TaskManager.markTaskCompleted(this, TaskManager.TaskType.PRONUNCIATION_TOPIC)
        }

        val feedbackMessage = """
            Điểm phát âm: ${feedback.score}/10
            Lỗi: $mistakesText
            Gợi ý sửa:
            $suggestionsText
            
            ${getTaskCompletionMessage()}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Kết quả chấm điểm")
            .setMessage(feedbackMessage)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                viewModel.clearPronunciationFeedback()
            }
            .show()
    }

    private fun getTaskCompletionMessage(): String {
        val scoreTaskCompleted = TaskManager.isTaskCompleted(this, TaskManager.TaskType.PRONUNCIATION_SCORE)
        val topicTaskCompleted = TaskManager.isTaskCompleted(this, TaskManager.TaskType.PRONUNCIATION_TOPIC)
        
        val messages = mutableListOf<String>()
        
        if (scoreTaskCompleted) {
            messages.add("✅ Đã hoàn thành nhiệm vụ đạt trên 8 điểm")
        }
        if (topicTaskCompleted) {
            messages.add("✅ Đã hoàn thành nhiệm vụ luyện phát âm chủ đề hôm nay")
        }
        
        return if (messages.isEmpty()) "" else "\n\nNhiệm vụ đã hoàn thành:\n${messages.joinToString("\n")}"
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}