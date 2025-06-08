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
import androidx.lifecycle.lifecycleScope
import com.example.lingobuddypck.R
import com.example.lingobuddypck.Repository.FirebaseWordRepository
import com.example.lingobuddypck.Services.Message
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var lastUsedLocaleForTTS: Locale? = null
    private var lastUsedVoiceForTTS: Voice? = null

    private var isTtsInitialized: Boolean = false
    private var currentActualMessages: List<Message> = listOf()
    private var englishVoice: Voice? = null
    private var vietnameseVoice: Voice? = null

    data class TextSegment(val text: String, val langCode: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_with_ai)

        inputMessage = findViewById(R.id.inputMessage)
        sendButton = findViewById(R.id.sendButton)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        recyclerView = findViewById(R.id.chatRecyclerView)
        micButton = findViewById(R.id.micButton)

        adapter = ChatAdapter(ArrayList(), this, FirebaseWordRepository(), onSpeakClick = { text ->
            lifecycleScope.launch {
                speakMultiLanguageText(text)
            }
        })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(ChatItemDecoration(50))

        textToSpeech = TextToSpeech(this, this)

        viewModel.chatMessages.observe(this, Observer { messages ->
            currentActualMessages = messages
            val lastMessageOriginal = messages.lastOrNull()
            if (usedSpeechToText && lastMessageOriginal != null && lastMessageOriginal.role == "assistant" && lastMessageOriginal.content != null) {
                lifecycleScope.launch {
                    speakMultiLanguageText(lastMessageOriginal.content)
                    usedSpeechToText = false
                }
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

            val allVoices = textToSpeech.voices
            for (voice in allVoices) {
                val lang = voice.locale.language
                val name = voice.name.lowercase()

                when {
                    lang == "en" && englishVoice == null && !voice.isNetworkConnectionRequired -> {
                        englishVoice = voice
                    }
                    (lang == "vi" || lang == "vie") && vietnameseVoice == null && !voice.isNetworkConnectionRequired -> {
                        vietnameseVoice = voice
                    }
                }
            }

            Log.d("TTS", "Giọng Anh: ${englishVoice?.name}")
            Log.d("TTS", "Giọng Việt: ${vietnameseVoice?.name}")
        }
    }


    suspend fun parseTextWithLanguageTags(
        inputText: String,
        languageIdentifier: LanguageIdentifier
    ): List<TextSegment> {
        // A contract must be precise. We first segment by the primary language tags.
        val initialSegments = mutableListOf<TextSegment>()
        val tagRegex = Regex("""</?en>""") // A regex to find all <en> or </en> tags.
        val tags = tagRegex.findAll(inputText).toList()
        val langStack = mutableListOf("vi") // The default language is assumed to be 'vi'.
        var currentIndex = 0

        for (match in tags) {
            // The text between the previous tag and the current one adheres to the prior context.
            val textBefore = inputText.substring(currentIndex, match.range.first)
            if (textBefore.isNotEmpty()) {
                initialSegments.add(TextSegment(textBefore, langStack.last()))
            }

            // We adjust our context based on the nature of the tag.
            if (match.value == "<en>") {
                langStack.add("en")
            } else if (match.value == "</en>") {
                // A closing tag must correspond to an opening one.
                if (langStack.lastOrNull() == "en") {
                    langStack.removeAt(langStack.lastIndex)
                }
                // Malformed tags are simply weathered by time, and we proceed.
            }
            currentIndex = match.range.last + 1
        }

        // The remainder of the text after the final tag.
        if (currentIndex < inputText.length) {
            val remainingText = inputText.substring(currentIndex)
            if (remainingText.isNotEmpty()) {
                initialSegments.add(TextSegment(remainingText, langStack.last()))
            }
        }

        // Now, with the foundation secure, we examine the finer details within each segment.
        val processedSegments = mutableListOf<TextSegment>()
        val bracketRegex = Regex("""\[(.*?)]""")

        for (segment in initialSegments) {
            if (segment.langCode == "en") {
                // For English segments, we must inspect the bracketed content.
                var lastPos = 0
                val englishText = segment.text

                bracketRegex.findAll(englishText).forEach { bracketMatch ->
                    val bracketRange = bracketMatch.range
                    val textBeforeBracket = englishText.substring(lastPos, bracketRange.first)
                    if (textBeforeBracket.isNotBlank()) {
                        processedSegments.add(TextSegment(textBeforeBracket, "en"))
                    }

                    val insideBracketText = bracketMatch.groups[1]?.value ?: ""
                    val langCode = withContext(Dispatchers.IO) {
                        // We fulfill the contract of language identification.
                        Tasks.await(languageIdentifier.identifyLanguage(insideBracketText))
                    }

                    if (langCode == "vi") {
                        processedSegments.add(TextSegment(insideBracketText, "vi"))
                    } else {
                        // If not Vietnamese, it remains within its bracketed, English context.
                        processedSegments.add(TextSegment("[${insideBracketText}]", "en"))
                    }
                    lastPos = bracketRange.last + 1
                }

                // The remainder of the English segment, after the final bracket.
                if (lastPos < englishText.length) {
                    val remaining = englishText.substring(lastPos)
                    if (remaining.isNotBlank()) {
                        processedSegments.add(TextSegment(remaining, "en"))
                    }
                }
            } else {
                // Vietnamese segments require no such detailed inspection.
                processedSegments.add(segment)
            }
        }

        // Finally, we consolidate what is contiguous. Like dust settling into stone, adjacent segments of the same nature should be unified.
        val mergedSegments = mutableListOf<TextSegment>()
        for (segment in processedSegments) {
            if (segment.text.isBlank()) continue

            if (mergedSegments.isNotEmpty() && mergedSegments.last().langCode == segment.langCode) {
                val lastSegment = mergedSegments.removeAt(mergedSegments.lastIndex)
                mergedSegments.add(TextSegment(lastSegment.text + segment.text, lastSegment.langCode))
            } else {
                mergedSegments.add(segment)
            }
        }

        return mergedSegments
            .map { TextSegment(cleanLeadingPunctuation(it.text), it.langCode) }
            .filter { it.text.isNotBlank() }
    }


    private suspend fun speakMultiLanguageText(fullText: String?) {
        if (fullText.isNullOrBlank()) {
            Log.d("TTS", "Full text is null or blank, nothing to speak.")
            return
        }
        if (!isTtsInitialized) {
            Toast.makeText(this, "TTS chưa sẵn sàng.", Toast.LENGTH_SHORT).show()
            Log.e("TTS", "TTS not initialized when trying to speak.")
            return
        }

        val languageIdentifier = LanguageIdentification.getClient()
        val segments = parseTextWithLanguageTags(fullText,languageIdentifier)
        if (segments.isEmpty() && fullText.isNotBlank()) {
            Log.d("TTS", "No <en> tags found in '$fullText'. Speaking raw text with current/default voice.")
            if (textToSpeech.voice == null && (textToSpeech.language.language != "vi" && textToSpeech.language.language != "vie")) {
                val fallbackLocale = Locale("vi", "VN")
                if (textToSpeech.isLanguageAvailable(fallbackLocale) >= TextToSpeech.LANG_AVAILABLE) {
                    textToSpeech.language = fallbackLocale
                }
            }
            textToSpeech.speak(fullText, TextToSpeech.QUEUE_FLUSH, null, "fallback_${System.currentTimeMillis()}")
            return
        }
        if (segments.isEmpty()) {
            Log.d("TTS", "No segments to speak after parsing.")
            return
        }

        textToSpeech.stop()

        var utteranceIdCounter = 0

        for (segment in segments) {
            val cleanedText = cleanLeadingPunctuation(segment.text)
            if (cleanedText.isBlank()) continue

            val langCode = segment.langCode.lowercase()
            val utteranceId = "utt_${System.currentTimeMillis()}_${utteranceIdCounter++}"

            var targetVoiceForSegment: Voice? = null
            val targetLocaleForSegment: Locale

            when (langCode) {
                "en" -> {
                    targetVoiceForSegment = englishVoice
                    targetLocaleForSegment = Locale.ENGLISH
                }
                "vi", "vie" -> {
                    targetVoiceForSegment = vietnameseVoice // Giọng tiếng Việt đã chọn trong onInit
                    targetLocaleForSegment = Locale("vi", "VN")
                }
                else -> {
                    Log.w("TTS", "Unknown langCode '$langCode' for segment: '${cleanedText}'. Using system default locale.")
                    targetLocaleForSegment = Locale.getDefault() // Hoặc bỏ qua segment này
                }
            }

            var voiceOrLangSetThisTurn = false

            // Ưu tiên sử dụng setVoice nếu có giọng đọc cụ thể
            if (targetVoiceForSegment != null) {
                // Chỉ set voice nếu nó khác với voice đã dùng lần trước (tối ưu hóa)
                if (lastUsedVoiceForTTS?.name != targetVoiceForSegment.name) {
                    val setResult = textToSpeech.setVoice(targetVoiceForSegment)
                    if (setResult == TextToSpeech.SUCCESS) {
                        lastUsedVoiceForTTS = targetVoiceForSegment
                        lastUsedLocaleForTTS = targetVoiceForSegment.locale // Cập nhật locale theo voice
                        voiceOrLangSetThisTurn = true
                        Log.d("TTS", "Voice set to: ${targetVoiceForSegment.name} for '${cleanedText}'")
                    } else {
                        Log.w("TTS", "Failed to set voice ${targetVoiceForSegment.name}. Will try setting language locale.")
                        lastUsedVoiceForTTS = null // Voice không set được
                    }
                } else {
                    voiceOrLangSetThisTurn = true // Voice giống lần trước, không cần set lại nhưng coi như đã set
                }
            }

            // Nếu không set được voice cụ thể, hoặc không có voice cụ thể, thì set language
            if (!voiceOrLangSetThisTurn || targetVoiceForSegment == null) {
                if (lastUsedLocaleForTTS != targetLocaleForSegment) {
                    val langResult = textToSpeech.setLanguage(targetLocaleForSegment)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "Language $targetLocaleForSegment not supported for: '$cleanedText'. Speech might use fallback.")
                    } else {
                        lastUsedLocaleForTTS = targetLocaleForSegment
                        Log.d("TTS", "Language set to: $targetLocaleForSegment for '${cleanedText}'")
                    }
                    lastUsedVoiceForTTS = null // Vì chúng ta đang dựa vào setLanguage, không phải voice cụ thể
                }
            }

            val result = textToSpeech.speak(
                cleanedText,
                TextToSpeech.QUEUE_ADD,
                null, // Bundle có thể là null nếu không dùng utterance listener
                utteranceId
            )

            if (result == TextToSpeech.ERROR) {
                Log.e("TTS", "Error speaking segment: '$cleanedText', langCode: $langCode")
            }
        }
    }

    private fun cleanLeadingPunctuation(text: String): String {
        if (text.isBlank()) {
            return ""
        }
        val regex = Regex("^[\\p{P}\\p{Z}]+")

        return text.replace(regex, "")
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