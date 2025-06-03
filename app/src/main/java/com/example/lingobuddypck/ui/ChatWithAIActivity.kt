package com.example.lingobuddypck.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.ViewModel.ChatViewModel
import com.example.lingobuddypck.adapter.ChatAdapter
import com.example.lingobuddypck.data.ChatItemDecoration
import android.Manifest
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.example.lingobuddypck.R
import com.example.lingobuddypck.Repository.FirebaseWordRepository
import com.example.lingobuddypck.Services.Message
import java.util.Locale


class ChatWithAIActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter
    private lateinit var inputMessage: EditText
    private lateinit var sendButton: Button
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var micButton: ImageButton
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognitionIntent: Intent
    private lateinit var speechActivityResultLauncher: ActivityResultLauncher<Intent>
    private val RECORD_AUDIO_PERMISSION_CODE = 123
    private lateinit var textToSpeech: TextToSpeech
    private var usedSpeechToText = false

    private var isTtsInitialized: Boolean = false
    private var currentActualMessages: List<Message> = listOf()

    data class TextSegment(val text: String, val langCode: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_with_ai)

        inputMessage = findViewById(R.id.inputMessage)
        sendButton = findViewById(R.id.sendButton)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        recyclerView = findViewById(R.id.chatRecyclerView)
        micButton = findViewById(R.id.micButton)

        adapter = ChatAdapter(ArrayList(),this,FirebaseWordRepository(), onSpeakClick = { text ->
            speakMultiLanguageText(text)
        })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(ChatItemDecoration(50))

        textToSpeech = TextToSpeech(this, this)

        viewModel.chatMessages.observe(this, Observer { messages ->
            currentActualMessages = messages
            val lastMessageOriginal = messages.lastOrNull()
            if (usedSpeechToText && lastMessageOriginal != null && lastMessageOriginal.role == "assistant" && lastMessageOriginal.content != null) {
                speakMultiLanguageText(lastMessageOriginal.content)
                usedSpeechToText = false
            }

            adapter.setMessages(messages)

            if (messages.isNotEmpty()) {
                recyclerView.scrollToPosition(messages.size - 1)
            }
            updateAdapterWithTypingIndicator(viewModel.isWaitingForResponse.value ?: false)
        })

        viewModel.isLoading.observe(this, Observer { isLoading ->
            loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
            sendButton.isEnabled = !isLoading
            micButton.isEnabled = !isLoading
        })

        viewModel.isWaitingForResponse.observe(this, Observer { isWaitingForResponse ->
            updateAdapterWithTypingIndicator(isWaitingForResponse)
        })

        sendButton.setOnClickListener { sendMessage() }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        usedSpeechToText = false
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
    }

    private fun updateAdapterWithTypingIndicator(isAiTyping: Boolean) {
        val displayList = ArrayList(currentActualMessages.filter { it.role != "typing_indicator" })

        if (isAiTyping) {
            displayList.add(Message("typing_indicator", null))
        }

        adapter.setMessages(displayList) // Cập nhật adapter với danh sách mới

        if (displayList.isNotEmpty()) {
            recyclerView.scrollToPosition(displayList.size - 1)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            Log.d("TTS", "TextToSpeech initialized successfully.")
            try {
                val availableLanguages = textToSpeech.availableLanguages
                Log.i("TTS", "Available languages: $availableLanguages")
            } catch (e: Exception) {
                Log.e("TTS", "Error listing languages: ${e.message}", e)
            }
        } else {
            isTtsInitialized = false
            Log.e("TTS", "TTS Initialization Failed! Status: $status")
            Toast.makeText(this, "Không thể khởi tạo TextToSpeech.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun parseTextWithLanguageTags(inputText: String): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        var currentIndex = 0
        val startTag = "<en>"
        val endTag = "</en>"
        while (currentIndex < inputText.length) {
            val enTagStartIndex = inputText.indexOf(startTag, currentIndex)
            if (enTagStartIndex == -1) {
                if (currentIndex < inputText.length) segments.add(TextSegment(inputText.substring(currentIndex).trim(), "vi"))
                break
            }
            if (enTagStartIndex > currentIndex) segments.add(TextSegment(inputText.substring(currentIndex, enTagStartIndex).trim(), "vi"))
            val enTagEndIndex = inputText.indexOf(endTag, enTagStartIndex + startTag.length)
            if (enTagEndIndex == -1) {
                Log.w("Parser", "Malformed <en> tag at index $enTagStartIndex.")
                if (enTagStartIndex < inputText.length) segments.add(TextSegment(inputText.substring(enTagStartIndex).trim(), "vi"))
                break
            }
            val englishText = inputText.substring(enTagStartIndex + startTag.length, enTagEndIndex).trim()
            segments.add(TextSegment(englishText, "en"))
            currentIndex = enTagEndIndex + endTag.length
        }
        return segments.filter { it.text.isNotBlank() }
    }

    private fun speakMultiLanguageText(fullText: String?) {
        if (fullText.isNullOrBlank()) return
        if (!isTtsInitialized) {
            Toast.makeText(this, "TTS chưa sẵn sàng.", Toast.LENGTH_SHORT).show()
            return
        }

        val segments = parseTextWithLanguageTags(fullText)
        if (segments.isEmpty()) {
            textToSpeech.speak(fullText, TextToSpeech.QUEUE_ADD, null, "fallback_${System.currentTimeMillis()}")
            return
        }

        textToSpeech.stop()
        var utteranceIdCounter = 0
        for (segment in segments) {
            val uniqueUtteranceId = "utt_${System.currentTimeMillis()}_${utteranceIdCounter++}"
            val locale = when (segment.langCode.lowercase()) {
                "en" -> Locale.ENGLISH
                "vi", "vie" -> Locale("vi", "VN")
                else -> Locale.getDefault()
            }

            if (textToSpeech.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                textToSpeech.language = locale
            }

            textToSpeech.speak(segment.text, TextToSpeech.QUEUE_ADD, null, uniqueUtteranceId)
        }
    }


    private fun sendMessage() {
        val message = inputMessage.text.toString().trim()
        if (message.isNotEmpty()) {
            viewModel.sendMessage(message)
            inputMessage.text.clear()
        } else {
            Toast.makeText(this, "Vui lòng nhập tin nhắn", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAudioPermissionAndStartRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
        } else {
            startSpeechRecognition()
        }
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