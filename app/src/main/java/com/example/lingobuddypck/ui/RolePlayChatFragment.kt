package com.example.lingobuddypck.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.Factory.RolePlayViewModelFactory
import com.example.lingobuddypck.R
import com.example.lingobuddypck.ViewModel.Repository.FirebaseWordRepository
import com.example.lingobuddypck.ViewModel.RolePlayChatViewModel
import com.example.lingobuddypck.adapter.ChatAdapter
import com.example.lingobuddypck.data.ChatItemDecoration
import java.util.Locale

class RolePlayChatFragment : Fragment() {

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
    private val RECORD_AUDIO_PERMISSION_CODE = 1001

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
    ): View {
        return inflater.inflate(R.layout.activity_chat_with_ai, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val aiRole = arguments?.getString("AIRole") ?: "Giáo viên"
        val userRole = arguments?.getString("UserRole") ?: "Giáo viên"
        val contextText = arguments?.getString("context") ?: "Lớp học tiếng Anh"

        val factory = RolePlayViewModelFactory(userRole, aiRole, contextText)
        viewModel = ViewModelProvider(this, factory)[RolePlayChatViewModel::class.java]

        // Initialize views
        inputMessage = view.findViewById(R.id.inputMessage)
        sendButton = view.findViewById(R.id.sendButton)
        loadingSpinner = view.findViewById(R.id.loadingSpinner)
        recyclerView = view.findViewById(R.id.chatRecyclerView)
        micButton = view.findViewById(R.id.micButton)

        // Setup RecyclerView
        adapter = ChatAdapter(mutableListOf(), requireContext(), FirebaseWordRepository())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(ChatItemDecoration(50))

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
            }
        }

        // Initialize SpeechRecognizer and Intent
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // You can change the language
        }

        usedSpeechToText = false // Reset in onViewCreated

        // Observe chat messages
        viewModel.chatMessages.observe(viewLifecycleOwner) { messages ->
            adapter.setMessages(messages)
            recyclerView.scrollToPosition(messages.size - 1)

            // If the last message is from the bot and user used voice input
            val lastMessage = messages.lastOrNull()
            if (usedSpeechToText && lastMessage != null && lastMessage.role == "assistant" && lastMessage.content != null) {
                detectLanguageAndSpeak(lastMessage.content)
                usedSpeechToText = false // Reset flag
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
            sendButton.isEnabled = !isLoading
            micButton.isEnabled = !isLoading
        }

        // Setup click listeners
        sendButton.setOnClickListener {
            sendMessage()
        }

        micButton.setOnClickListener {
            checkAudioPermissionAndStartRecognition()
        }
    }

    private fun detectLanguageAndSpeak(text: String) {
        val languageIdentifier = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (languageCode != "und") {
                    val locale = Locale(languageCode)
                    if (textToSpeech.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                        textToSpeech.language = locale
                    }
                    speakOut(text)
                } else {
                    Log.d("TTS", "Unable to identify language")
                    speakOut(text) // Fallback
                }
            }
            .addOnFailureListener {
                Log.e("TTS", "Language detection failed", it)
                speakOut(text) // Fallback
            }
    }

    private fun speakOut(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
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
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        } else {
            startSpeechRecognition()
        }
    }

    private fun startSpeechRecognition() {
        speechActivityResultLauncher.launch(speechRecognitionIntent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                Toast.makeText(requireContext(), "Cần cấp quyền thu âm để sử dụng tính năng này", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer.destroy()
        textToSpeech.shutdown()
    }
}