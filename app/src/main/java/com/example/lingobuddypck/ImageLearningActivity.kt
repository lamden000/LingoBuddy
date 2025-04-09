package com.example.lingobuddypck

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.lingobuddypck.ViewModel.ImageLearningViewModel

class ImageLearningActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var inputMessage: EditText
    private lateinit var sendButton: Button
    private lateinit var responseTextView: TextView
    private lateinit var viewModel: ImageLearningViewModel

    private var selectedImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            imageView.setImageURI(selectedImageUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_learning)

        viewModel = ViewModelProvider(this)[ImageLearningViewModel::class.java]

        imageView = findViewById(R.id.imageView)
        inputMessage = findViewById(R.id.inputMessage)
        sendButton = findViewById(R.id.sendButton)
        responseTextView = findViewById(R.id.responseTextView)

        imageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        sendButton.setOnClickListener {
            if (selectedImageUri == null) {
                Toast.makeText(this, "Please select an image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val message = inputMessage.text.toString().trim()
            viewModel.sendImageAndMessage(this, message, selectedImageUri!!)
        }

        viewModel.responseText.observe(this) {
            responseTextView.text = it
        }

        viewModel.loading.observe(this) {
            sendButton.isEnabled = !it
            sendButton.text = if (it) "Đang gửi..." else "Gửi"
        }
    }
}