package com.example.lingobuddypck

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


class ChatWithAIActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_with_ai)

        inputMessage = findViewById(R.id.inputMessage)
        sendButton = findViewById(R.id.sendButton)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        recyclerView = findViewById(R.id.chatRecyclerView)
        micButton = findViewById(R.id.micButton)

        adapter = ChatAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(ChatItemDecoration(50))

        viewModel.chatMessages.observe(this, Observer { messages ->
            adapter.setMessages(messages)
            recyclerView.scrollToPosition(messages.size - 1)
        })

        viewModel.isLoading.observe(this, Observer { isLoading ->
            loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
            sendButton.isEnabled = !isLoading
            micButton.isEnabled = !isLoading
        })

        sendButton.setOnClickListener {
            sendMessage()
        }

        // Initialize SpeechRecognizer and Intent
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // You can change the language
        }

        // ActivityResultLauncher for speech recognition
        speechActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    val results = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    results?.let {
                        if (it.isNotEmpty()) {
                            inputMessage.setText(it[0])
                        }
                    }
                } else {
                    Toast.makeText(this, "Không nhận diện được giọng nói", Toast.LENGTH_SHORT).show()
                }
            }

        micButton.setOnClickListener {
            checkAudioPermissionAndStartRecognition()
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
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
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
                Toast.makeText(this, "Cần cấp quyền thu âm để sử dụng tính năng này", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}