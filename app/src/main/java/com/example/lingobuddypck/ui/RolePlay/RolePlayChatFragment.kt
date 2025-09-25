package com.example.lingobuddypck.ui.RolePlay

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.Services.RolePlayViewModelFactory
import com.example.lingobuddypck.R
import com.example.lingobuddypck.Repository.FirebaseWordRepository
import com.example.lingobuddypck.Services.Message
import com.example.lingobuddypck.ViewModel.RolePlayChatViewModel
import com.example.lingobuddypck.adapter.ChatAdapter
import com.example.lingobuddypck.data.ChatItemDecoration
import com.example.lingobuddypck.utils.TaskManager
import java.util.Locale


class RolePlayChatFragment : Fragment(), TextToSpeech.OnInitListener {

    private lateinit var viewModel: RolePlayChatViewModel
    private lateinit var adapter: ChatAdapter
    private lateinit var inputMessage: EditText
    private lateinit var sendButton: Button
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var micButton: ImageButton
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognitionIntent: Intent
    private var usedSpeechToText = false
    private var currentActualMessages: List<Message> = listOf()
    private var conversationStartTime: Long = 0
    private var isTimerRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsedMinutes = (System.currentTimeMillis() - conversationStartTime) / 60000
            if (elapsedMinutes >= 10 && TaskManager.isTaskInToday(requireContext(), TaskManager.TaskType.ROLE_PLAY_TEN_MINUTES)) {
                TaskManager.markTaskCompleted(requireContext(), TaskManager.TaskType.ROLE_PLAY_TEN_MINUTES)
                isTimerRunning = false
            } else if (isTimerRunning) {
                handler.postDelayed(this, 60000) // Check every minute
            }
        }
    }

    // Biến cho TTS đa ngôn ngữ
    private var isTtsInitialized: Boolean = false
    private var vietnameseVoice: Voice? = null
    private var englishVoice: Voice? = null

    data class TextSegment(val text: String, val langCode: String)

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startSpeechRecognition()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Cần cấp quyền thu âm để sử dụng tính năng này",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val speechActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val results = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                results?.let {
                    if (it.isNotEmpty()) {
                        inputMessage.setText(it[0])
                        usedSpeechToText = true
                        sendMessage()
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Không nhận diện được giọng nói", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_chat_with_ai, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val aiRole = arguments?.getString("AIRole") ?: "Teacher"
        val userRole = arguments?.getString("UserRole") ?: "Student"
        val contextText = arguments?.getString("context") ?: "English conversation practice"

        // Start conversation timer
        conversationStartTime = System.currentTimeMillis()
        isTimerRunning = true
        handler.postDelayed(timerRunnable, 60000) // First check after 1 minute

        val factory = RolePlayViewModelFactory(userRole, aiRole, contextText)
        viewModel = ViewModelProvider(this, factory)[RolePlayChatViewModel::class.java]

        inputMessage = view.findViewById(R.id.inputMessage)
        sendButton = view.findViewById(R.id.sendButton)
        loadingSpinner = view.findViewById(R.id.loadingSpinner)
        recyclerView = view.findViewById(R.id.chatRecyclerView)
        micButton = view.findViewById(R.id.micButton)

        adapter = ChatAdapter(mutableListOf(),requireContext(), FirebaseWordRepository(), onSpeakClick = { text ->
            speakMultiLanguageText(text)
        })
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(ChatItemDecoration(50))

        // Khởi tạo TextToSpeech
        textToSpeech = TextToSpeech(requireContext(), this)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
        }
        usedSpeechToText = false

        viewModel.chatMessages.observe(viewLifecycleOwner, Observer { messages ->

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

        viewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
            loadingSpinner.visibility = if (isLoading == true) View.VISIBLE else View.GONE
            sendButton.isEnabled = !(isLoading ?: false)
            micButton.isEnabled = !(isLoading ?: false)
        })

        viewModel.isWaitingForResponse.observe(requireActivity(), Observer { isWaitingForResponse ->
            updateAdapterWithTypingIndicator(isWaitingForResponse)
        })

        sendButton.setOnClickListener {
            sendMessage()
        }

        micButton.setOnClickListener {
            checkAudioPermissionAndStartRecognition()
        }
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
                val voices = textToSpeech.voices
                for (voice in voices) {
                    when (voice.locale.language) {
                        "vi", "vie" -> if (vietnameseVoice == null || !voice.isNetworkConnectionRequired) vietnameseVoice = voice
                        "en" -> if (englishVoice == null || !voice.isNetworkConnectionRequired) englishVoice = voice
                    }
                }

                if (vietnameseVoice == null) {
                    Log.w("TTS", "Không tìm thấy giọng Việt, fallback Locale VI")
                    textToSpeech.language = Locale("vi", "VN")
                } else {
                    Log.i("TTS", "Giọng Việt: ${vietnameseVoice!!.name}")
                }

                if (englishVoice == null) {
                    Log.w("TTS", "Không tìm thấy giọng Anh, fallback Locale EN")
                    textToSpeech.language = Locale.ENGLISH
                } else {
                    Log.i("TTS", "Giọng Anh: ${englishVoice!!.name}")
                }

            } catch (e: Exception) {
                Log.e("TTS", "Lỗi khi lấy hoặc set giọng: ${e.message}", e)
            }

        } else {
            isTtsInitialized = false
            Log.e("TTS", "TTS Initialization Failed! Status: $status")
            Toast.makeText(requireContext(), "Không thể khởi tạo TextToSpeech.", Toast.LENGTH_SHORT).show()
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
                Log.w("Parser", "Malformed <en> tag at index $enTagStartIndex. Treating rest as Vietnamese.")
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
        if (fullText.isNullOrBlank()) {
            Log.d("TTS", "speakMultiLanguageText: fullText is null or blank.")
            return
        }
        if (!isTtsInitialized) {
            Toast.makeText(requireContext(), "TTS chưa sẵn sàng.", Toast.LENGTH_SHORT).show()
            Log.e("TTS", "TTS not ready when trying to speak.")
            return
        }

        val segments = parseTextWithLanguageTags(fullText)
        if (segments.isEmpty() && fullText.isNotBlank()) { // Xử lý trường hợp không có tag nhưng vẫn có text
            Log.d("TTS", "No <en> tags found. Speaking raw text with default (likely Vietnamese) voice.")
            if (vietnameseVoice != null) textToSpeech.voice = vietnameseVoice
            else textToSpeech.language = Locale("vi", "VN")
            textToSpeech.speak(fullText, TextToSpeech.QUEUE_FLUSH, null, "fallback_${System.currentTimeMillis()}")
            return
        }
        if (segments.isEmpty()){ // Hoàn toàn không có gì để nói
            return
        }


        textToSpeech.stop() // Dừng và xóa hàng đợi trước khi nói các đoạn mới

        var utteranceIdCounter = 0
        for (segment in segments) {
            val uniqueUtteranceId = "utt_${System.currentTimeMillis()}_${utteranceIdCounter++}"
            val params = Bundle() // Để đó nếu sau này cần dùng với UtteranceProgressListener

            if (segment.langCode == "en") {
                if (englishVoice != null) {
                    val setResult = textToSpeech.setVoice(englishVoice)
                    if (setResult != TextToSpeech.SUCCESS) {
                        Log.w("TTS", "Failed to set specific English voice for '${segment.text}', falling back to Locale.")
                        textToSpeech.language = Locale.ENGLISH
                    }
                } else {
                    textToSpeech.language = Locale.ENGLISH
                    Log.w("TTS", "No specific English voice, using Locale.ENGLISH for '${segment.text}'")
                }
                Log.d("TTS", "Speaking English: '${segment.text}'")
                textToSpeech.speak(segment.text, TextToSpeech.QUEUE_ADD, params, uniqueUtteranceId)
            } else { // Mặc định là "vi"
                if (vietnameseVoice != null) {
                    val setResult = textToSpeech.setVoice(vietnameseVoice)
                    if (setResult != TextToSpeech.SUCCESS) {
                        Log.w("TTS", "Failed to set specific Vietnamese voice for '${segment.text}', falling back to Locale.")
                        textToSpeech.language = Locale("vi", "VN")
                    }
                } else {
                    textToSpeech.language = Locale("vi", "VN")
                    Log.w("TTS", "No specific Vietnamese voice, using Locale VIETNAMESE for '${segment.text}'")
                }
                Log.d("TTS", "Speaking Vietnamese: '${segment.text}'")
                textToSpeech.speak(segment.text, TextToSpeech.QUEUE_ADD, params, uniqueUtteranceId)
            }
        }
    }

    private fun sendMessage() {
        val message = inputMessage.text.toString().trim()
        if (message.isNotEmpty()) {
            viewModel.sendMessage(message)
            inputMessage.text.clear()
        } else {
            Toast.makeText(requireContext(), "Vui lòng nhập tin nhắn", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAudioPermissionAndStartRecognition() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startSpeechRecognition()
            }
            else -> {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startSpeechRecognition() {
        try {
            speechActivityResultLauncher.launch(speechRecognitionIntent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Lỗi khởi động nhận diện giọng nói: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("STT", "Error launching speech recognizer", e)
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::textToSpeech.isInitialized && textToSpeech.isSpeaking) {
            textToSpeech.stop()
            Log.d("TTS", "TTS stopped onPause.")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (this::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        if (this::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
            Log.d("TTS", "TTS shutdown onDestroyView.")
        }
        
        // Stop the timer
        isTimerRunning = false
        handler.removeCallbacks(timerRunnable)
        
        // _binding = null // Nếu bạn sử dụng ViewBinding, hãy thêm dòng này
    }
}