package com.example.lingobuddypck.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.R
import com.example.lingobuddypck.ViewModel.ImageLearningViewModel
import com.example.lingobuddypck.Repository.FirebaseWordRepository
import com.example.lingobuddypck.Services.Message
import com.example.lingobuddypck.adapter.ChatAdapter
import com.example.lingobuddypck.data.ImageQuiz
import com.example.lingobuddypck.data.ImageQuizQuestion
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageLearningActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var inputMessage: EditText
    private lateinit var sendButton: Button
    private lateinit var micButton: ImageButton
    private lateinit var selectImageButton: Button
    private lateinit var openCameraButton: Button
    private lateinit var imagePreview: ImageView
    private lateinit var viewModel: ImageLearningViewModel
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognitionIntent: Intent
    private lateinit var speechActivityResultLauncher: ActivityResultLauncher<Intent>
    private val RECORD_AUDIO_PERMISSION_CODE = 123

    private var selectedImageUri: Uri? = null
    private var tempImageUri: Uri? = null
    private var currentActualMessages: List<Message> = listOf()
    private var usedSpeechToText = false

    private lateinit var generateQuizButton: Button
    private lateinit var quizContainer: LinearLayout
    private lateinit var quizQuestionsContainer: LinearLayout
    private lateinit var submitQuizButton: Button
    private val questionViews = mutableMapOf<String, RadioGroup>()

    private lateinit var quizContainerView: ScrollView
    private lateinit var quizImageView: ImageView

    private lateinit var inputContainer: LinearLayout
    private lateinit var actionButtonsContainer: LinearLayout

    private lateinit var imagePreviewContainer: FrameLayout
    private lateinit var closeImageButton: ImageButton

    // Launcher for picking image from gallery
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                imagePreview.setImageURI(selectedImageUri)
                imagePreviewContainer.visibility = View.VISIBLE
                generateQuizButton.isEnabled = true // Enable quiz generation when image is selected
                viewModel.clearQuiz() // Clear any existing quiz
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            selectedImageUri = tempImageUri
            imagePreview.setImageURI(selectedImageUri)
            imagePreviewContainer.visibility = View.VISIBLE
            generateQuizButton.isEnabled = true // Enable quiz generation when image is captured
            viewModel.clearQuiz() // Clear any existing quiz
        } else {
            tempImageUri = null
        }
    }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to take pictures.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_learning)

        viewModel = ViewModelProvider(this)[ImageLearningViewModel::class.java]
        recyclerView = findViewById(R.id.recyclerView)
        inputMessage = findViewById(R.id.inputMessage)
        sendButton = findViewById(R.id.sendButton)
        selectImageButton = findViewById(R.id.selectImageButton)
        openCameraButton = findViewById(R.id.openCameraButton)
        imagePreview = findViewById(R.id.imagePreview)
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer)
        closeImageButton = findViewById(R.id.closeImageButton)
        textToSpeech = TextToSpeech(this, this)
        micButton = findViewById(R.id.micButtonIMG)

        generateQuizButton = findViewById(R.id.generateQuizButton)
        quizContainerView = findViewById(R.id.quizContainerView)
        quizContainer = findViewById(R.id.quizContainer)
        quizQuestionsContainer = findViewById(R.id.quizQuestionsContainer)
        submitQuizButton = findViewById(R.id.submitQuizButton)
        quizImageView = findViewById(R.id.quizImageView)
        
        // Get references to button containers
        inputContainer = findViewById(R.id.inputContainer)
        actionButtonsContainer = findViewById(R.id.actionButtonsContainer)

        recyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(mutableListOf(), this, FirebaseWordRepository(),
            onSpeakClick = { text ->
                detectAndSpeak(text)})
        recyclerView.adapter = chatAdapter

        selectImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }

        openCameraButton.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    launchCamera()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                    Toast.makeText(this, "Camera access is needed to take photos.", Toast.LENGTH_LONG).show()
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
                else -> {
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }

        sendButton.setOnClickListener {
            sendMessage()
        }

        speechActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    val results = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    results?.let {
                        if (it.isNotEmpty()) {
                            inputMessage.setText(it[0])
                            usedSpeechToText = true
                            sendMessage()
                        }
                    }
                } else {
                    Toast.makeText(this, "Không nhận diện được giọng nói", Toast.LENGTH_SHORT).show()
                }
            }
        micButton.setOnClickListener { checkAudioPermissionAndStartRecognition() }

        viewModel.chatMessages.observe(this) { chatMessages ->
            currentActualMessages = chatMessages

            val lastMessageOriginal = chatMessages.lastOrNull()
            if (usedSpeechToText && lastMessageOriginal != null && lastMessageOriginal.role == "AI" && lastMessageOriginal.content != null) {
                detectAndSpeak(lastMessageOriginal.content)
                usedSpeechToText = false
            }

            chatAdapter.setMessages(chatMessages)
            if (chatMessages.isNotEmpty()) {
                recyclerView.scrollToPosition(chatMessages.size - 1)
            }
            updateAdapterWithTypingIndicator(viewModel.isWaitingForResponse.value ?: false)
        }

        viewModel.isWaitingForResponse.observe(this, Observer { isWaitingForResponse ->
            updateAdapterWithTypingIndicator(isWaitingForResponse)
        })

        viewModel.loading.observe(this) {
            sendButton.isEnabled = !it
            inputMessage.isEnabled = !it // Also disable input field during loading
            selectImageButton.isEnabled = !it
            openCameraButton.isEnabled = !it
            sendButton.text = if (it) "Sending..." else "Send"
        }

        setupQuizUI()
        observeQuizState()

        closeImageButton.setOnClickListener {
            clearImage()
        }
    }

    private fun setupQuizUI() {
        generateQuizButton.setOnClickListener {
            if (selectedImageUri != null) {
                viewModel.generateQuizFromImage(this, selectedImageUri!!)
                imagePreviewContainer.visibility = View.GONE
            }
        }

        submitQuizButton.setOnClickListener {
            submitQuizAnswers()
        }
    }

    private fun observeQuizState() {
        viewModel.isGeneratingQuiz.observe(this) { isGenerating ->
            generateQuizButton.isEnabled = selectedImageUri != null && !isGenerating
            if (isGenerating) {
                showQuizLoadingState()
            }
        }

        viewModel.currentQuiz.observe(this) { quiz ->
            if (quiz != null) {
                showQuiz(quiz)
            } else {
                hideQuiz()
            }
        }

        viewModel.quizScore.observe(this) { score ->
            if (score != null) {
                showQuizResults(score.first, score.second)
            }
        }
    }

    private fun showQuizLoadingState() {
        recyclerView.visibility = View.GONE
        quizContainerView.visibility = View.VISIBLE
        quizContainer.visibility = View.VISIBLE
        quizQuestionsContainer.removeAllViews()
        val loadingView = LayoutInflater.from(this).inflate(R.layout.quiz_loading_view, quizQuestionsContainer, false)
        quizQuestionsContainer.addView(loadingView)
        submitQuizButton.visibility = View.GONE
        
        // Hide input and action buttons
        inputContainer.visibility = View.GONE
        actionButtonsContainer.visibility = View.GONE
    }

    private fun showQuiz(quiz: ImageQuiz) {
        recyclerView.visibility = View.GONE
        quizContainerView.visibility = View.VISIBLE
        quizContainer.visibility = View.VISIBLE
        quizQuestionsContainer.removeAllViews()
        questionViews.clear()

        // Set the quiz image
        selectedImageUri?.let { uri ->
            quizImageView.setImageURI(uri)
            quizImageView.visibility = View.VISIBLE
        }

        // Add questions (skipping the description)
        quiz.questions.forEach { question ->
            addQuestionToLayout(question)
        }

        submitQuizButton.visibility = View.VISIBLE
        
        // Hide input and action buttons
        inputContainer.visibility = View.GONE
        actionButtonsContainer.visibility = View.GONE
    }

    private fun addQuestionToLayout(question: ImageQuizQuestion) {
        val questionLayout = LayoutInflater.from(this)
            .inflate(R.layout.item_quiz_question, quizQuestionsContainer, false)

        val questionText = questionLayout.findViewById<TextView>(R.id.questionText)
        val optionsGroup = questionLayout.findViewById<RadioGroup>(R.id.optionsGroup)

        questionText.text = question.question
        
        question.options.forEach { (key, text) ->
            val radioButton = RadioButton(this).apply {
                id = View.generateViewId()
                this.text = "$key. $text"
                layoutParams = RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 16, 16, 16)
            }
            optionsGroup.addView(radioButton)
        }

        questionViews[question.id] = optionsGroup
        quizQuestionsContainer.addView(questionLayout)
    }

    private fun submitQuizAnswers() {
        val answers = mutableMapOf<String, String>()
        
        questionViews.forEach { (questionId, radioGroup) ->
            val selectedId = radioGroup.checkedRadioButtonId
            if (selectedId != -1) {
                val radioButton = findViewById<RadioButton>(selectedId)
                val answerKey = radioButton.text.toString().substringBefore(".").trim()
                answers[questionId] = answerKey.lowercase()
            }
        }

        if (answers.size < questionViews.size) {
            Toast.makeText(this, "Vui lòng trả lời tất cả câu hỏi", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.submitQuizAnswers(answers)
    }

    private fun showQuizResults(score: Int, total: Int) {
        val quiz = viewModel.currentQuiz.value ?: return
        val quizResult = viewModel.quizResult.value ?: return
        
        quiz.questions.forEach { question ->
            val radioGroup = questionViews[question.id] ?: return@forEach
            val selectedId = radioGroup.checkedRadioButtonId
            if (selectedId != -1) {
                val radioButton = findViewById<RadioButton>(selectedId)
                val selectedAnswer = radioButton.text.toString().substringBefore(".").trim().lowercase()
                val feedback = quizResult.feedback[question.id]
                
                // Add feedback view below the question
                val feedbackView = TextView(this).apply {
                    val isCorrect = feedback?.status == "correct"
                    text = if (isCorrect) {
                        "✅ Chính xác!"
                    } else {
                        val correctOption = question.options[question.correctAnswer] ?: ""
                        "❌ Đáp án đúng là: ${question.correctAnswer.uppercase()}. $correctOption\n\n${feedback?.explanation ?: ""}"
                    }
                    setTextColor(if (isCorrect) Color.GREEN else Color.RED)
                    setPadding(32, 8, 32, 16)
                }
                
                // Find the question layout and add feedback
                val questionLayout = radioGroup.parent as ViewGroup
                questionLayout.addView(feedbackView)
                
                // Disable all radio buttons after showing results
                for (i in 0 until radioGroup.childCount) {
                    radioGroup.getChildAt(i).isEnabled = false
                }
            }
        }

        // Show final score with percentage
        val percentage = (score.toFloat() / total * 100).toInt()
        AlertDialog.Builder(this)
            .setTitle("Kết quả")
            .setMessage("Bạn đã trả lời đúng $score/$total câu! ($percentage%)")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()

        // Disable submit button after showing results
        submitQuizButton.isEnabled = false
        
        // Add a "Try Again" button
        submitQuizButton.text = "Kết thúc"
        submitQuizButton.isEnabled = true
        submitQuizButton.setOnClickListener {
            hideQuiz()
            clearImage()
            viewModel.clearQuiz()
            submitQuizButton.text = "Nộp bài"
            submitQuizButton.setOnClickListener { submitQuizAnswers() }
        }
    }

    private fun hideQuiz() {
        quizContainerView.visibility = View.GONE
        quizContainer.visibility = View.GONE
        quizQuestionsContainer.removeAllViews()
        submitQuizButton.visibility = View.GONE
        quizImageView.visibility = View.GONE
        quizImageView.setImageURI(null)
        questionViews.clear()
        recyclerView.visibility = View.VISIBLE
        
        // Show input and action buttons
        inputContainer.visibility = View.VISIBLE
        actionButtonsContainer.visibility = View.VISIBLE
    }

    @Throws(IOException::class)
    private fun createImageFileUri(): Uri {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        if (storageDir == null || (!storageDir.exists() && !storageDir.mkdirs())) {
            throw IOException("Failed to create directory for image.")
        }

        val imageFile = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )

        return FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            imageFile
        )
    }

    private fun updateAdapterWithTypingIndicator(isAiTyping: Boolean) {
        val displayList = ArrayList(currentActualMessages.filter { it.role != "typing_indicator" })

        if (isAiTyping) {
            displayList.add(Message("typing_indicator", null))
        }

        chatAdapter.setMessages(displayList)

        if (displayList.isNotEmpty()) {
            recyclerView.scrollToPosition(displayList.size - 1)
        }
    }

    private fun checkAudioPermissionAndStartRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
        } else {
            startSpeechRecognition()
        }
    }

    private fun sendMessage() {
        val message = inputMessage.text.toString().trim()
        if (message.isEmpty() && selectedImageUri == null) {
            Toast.makeText(this, "Please enter a message or select an image", Toast.LENGTH_SHORT).show()
            return
        }

        // Hide quiz if showing
        hideQuiz()

        viewModel.sendImageAndMessage(this, message, selectedImageUri)

        inputMessage.text.clear()
        imagePreviewContainer.visibility = View.GONE
        clearImage()
    }

    private fun clearImage() {
        imagePreview.setImageURI(null)
        selectedImageUri = null
        tempImageUri = null
        imagePreviewContainer.visibility = View.GONE
        generateQuizButton.isEnabled = false
    }

    private fun startSpeechRecognition() {
        try {
            speechActivityResultLauncher.launch(speechRecognitionIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khởi động nhận diện giọng nói: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("STT", "Error launching speech recognizer", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "Cần cấp quyền thu âm để sử dụng tính năng này", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun launchCamera() {
        try {
            tempImageUri = createImageFileUri()
            takePictureLauncher.launch(tempImageUri!!)
        } catch (ex: IOException) {
            // Error occurred while creating the File
            Toast.makeText(this, "Error creating image file: ${ex.message}", Toast.LENGTH_LONG).show()
            tempImageUri = null // Reset if URI creation failed
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d("TTS", "TextToSpeech initialized successfully.")
            try {
                val availableLanguages = textToSpeech.availableLanguages
                Log.i("TTS", "Available languages: $availableLanguages")
            } catch (e: Exception) {
                Log.e("TTS", "Error listing languages: ${e.message}", e)
            }
        } else {
            Log.e("TTS", "TTS Initialization Failed! Status: $status")
            Toast.makeText(this, "Không thể khởi tạo TextToSpeech.", Toast.LENGTH_SHORT).show()
        }
    }

    fun detectAndSpeak(text: String?) {
        val languageIdentifier = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()

        if (text != null) {
            languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener { languageCode ->
                    if (languageCode != "und") {
                        val locale = Locale(languageCode)
                        if (textToSpeech.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                            textToSpeech.language = locale
                            textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, null)
                        } else {
                            Log.w("TTS", "Ngôn ngữ $languageCode không được hỗ trợ bởi TTS.")
                            fallbackSpeak(text, textToSpeech)
                        }
                    } else {
                        Log.w("TTS", "Không xác định được ngôn ngữ.")
                        fallbackSpeak(text, textToSpeech)
                    }
                }
                .addOnFailureListener {
                    Log.e("TTS", "Lỗi khi xác định ngôn ngữ", it)
                    fallbackSpeak(text, textToSpeech)
                }
        }
    }

    private fun fallbackSpeak(text: String, textToSpeech: TextToSpeech) {
        textToSpeech.language = Locale("vi", "VN")
        textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }
    override fun onPause() {
        super.onPause()
        if (this::textToSpeech.isInitialized && textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::speechRecognizer.isInitialized) speechRecognizer.destroy()
        if (this::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
}