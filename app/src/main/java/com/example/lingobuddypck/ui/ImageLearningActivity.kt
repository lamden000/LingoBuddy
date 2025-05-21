package com.example.lingobuddypck.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.R
import com.example.lingobuddypck.ViewModel.ImageLearningViewModel
import com.example.lingobuddypck.Repository.FirebaseWordRepository
import com.example.lingobuddypck.adapter.ChatAdapter

class ImageLearningActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var inputMessage: EditText
    private lateinit var sendButton: Button
    private lateinit var selectImageButton: Button
    private lateinit var imagePreview: ImageView
    private lateinit var viewModel: ImageLearningViewModel
    private lateinit var chatAdapter: ChatAdapter

    private var selectedImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            // Display the image preview
            imagePreview.setImageURI(selectedImageUri)
            imagePreview.visibility = View.VISIBLE
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
        imagePreview = findViewById(R.id.imagePreview)

        // Initialize the RecyclerView and adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(mutableListOf(),this, FirebaseWordRepository())  // Initialize the adapter with an empty list
        recyclerView.adapter = chatAdapter

        // Handle image button click to select an image
        selectImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        sendButton.setOnClickListener {
            val message = inputMessage.text.toString().trim()
            if (message.isEmpty() && selectedImageUri == null) {
                Toast.makeText(this, "Please enter a message or select an image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Send message and image URI to the ViewModel
            viewModel.sendImageAndMessage(this, message, selectedImageUri)

            // Optionally, clear input message field and reset image preview after sending
            inputMessage.text.clear()
            imagePreview.visibility = View.GONE
            selectedImageUri = null
        }

        // Observe the chat responses
        viewModel.chatMessages.observe(this) { chatMessages ->
            chatAdapter.setMessages(chatMessages)
        }

        // Handle the loading state of sending a message
        viewModel.loading.observe(this) {
            sendButton.isEnabled = !it
            sendButton.text = if (it) "Sending..." else "Send"
        }
    }
}