package com.example.lingobuddypck.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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

    // Launcher for picking image from gallery
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                imagePreview.setImageURI(selectedImageUri)
                imagePreview.visibility = View.VISIBLE
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            selectedImageUri = tempImageUri
            imagePreview.setImageURI(selectedImageUri)
            imagePreview.visibility = View.VISIBLE
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
        textToSpeech = TextToSpeech(this, this)
        micButton = findViewById(R.id.micButtonIMG)

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

        viewModel.sendImageAndMessage(this, message, selectedImageUri)

        inputMessage.text.clear()
        imagePreview.visibility = View.GONE
        imagePreview.setImageURI(null) // Clear image explicitly
        selectedImageUri = null
        tempImageUri = null
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