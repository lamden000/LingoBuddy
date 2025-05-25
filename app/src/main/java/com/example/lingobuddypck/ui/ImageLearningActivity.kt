package com.example.lingobuddypck.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.R
import com.example.lingobuddypck.ViewModel.ImageLearningViewModel
import com.example.lingobuddypck.Repository.FirebaseWordRepository
import com.example.lingobuddypck.adapter.ChatAdapter
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageLearningActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var inputMessage: EditText
    private lateinit var sendButton: Button
    private lateinit var selectImageButton: Button
    private lateinit var openCameraButton: Button // 🆕 New button for camera
    private lateinit var imagePreview: ImageView
    private lateinit var viewModel: ImageLearningViewModel
    private lateinit var chatAdapter: ChatAdapter

    private var selectedImageUri: Uri? = null
    private var tempImageUri: Uri? = null // To store URI for the image to be captured by camera

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

    // 🆕 Launcher for taking a picture
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            selectedImageUri = tempImageUri // The image was saved to tempImageUri
            imagePreview.setImageURI(selectedImageUri)
            imagePreview.visibility = View.VISIBLE
        } else {
            // Handle failure or cancellation, tempImageUri might be null or file not created
            tempImageUri = null // Reset if capture failed
            // Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
        }
    }

    // 🆕 Launcher for camera permission request
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                launchCamera() // Permission granted, launch camera
            } else {
                Toast.makeText(this, "Camera permission is required to take pictures.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_learning) // Ensure this layout has openCameraButton

        viewModel = ViewModelProvider(this)[ImageLearningViewModel::class.java]

        recyclerView = findViewById(R.id.recyclerView)
        inputMessage = findViewById(R.id.inputMessage)
        sendButton = findViewById(R.id.sendButton)
        selectImageButton = findViewById(R.id.selectImageButton)
        openCameraButton = findViewById(R.id.openCameraButton) // 🚨 Initialize your new button
        imagePreview = findViewById(R.id.imagePreview)

        recyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(mutableListOf(), this, FirebaseWordRepository())
        recyclerView.adapter = chatAdapter

        selectImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        // 🆕 Handle open camera button click
        openCameraButton.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission is already granted, launch camera
                    launchCamera()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                    // Explain to the user why the permission is needed (optional)
                    Toast.makeText(this, "Camera access is needed to take photos.", Toast.LENGTH_LONG).show()
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
                else -> {
                    // Directly request the permission
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }

        sendButton.setOnClickListener {
            val message = inputMessage.text.toString().trim()
            if (message.isEmpty() && selectedImageUri == null) {
                Toast.makeText(this, "Please enter a message or select an image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.sendImageAndMessage(this, message, selectedImageUri)

            inputMessage.text.clear()
            imagePreview.visibility = View.GONE
            imagePreview.setImageURI(null) // Clear image explicitly
            selectedImageUri = null
            tempImageUri = null
        }

        viewModel.chatMessages.observe(this) { chatMessages ->
            chatAdapter.setMessages(chatMessages)
        }

        viewModel.loading.observe(this) {
            sendButton.isEnabled = !it
            inputMessage.isEnabled = !it // Also disable input field during loading
            selectImageButton.isEnabled = !it
            openCameraButton.isEnabled = !it
            sendButton.text = if (it) "Sending..." else "Send"
        }
    }

    // 🆕 Method to create an image file and its URI
    @Throws(IOException::class)
    private fun createImageFileUri(): Uri {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        // Use external cache directory to avoid needing explicit storage permissions for app-private files
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        // If you want to use external cache dir:
        // val storageDir: File? = externalCacheDir

        if (storageDir == null || (!storageDir.exists() && !storageDir.mkdirs())) {
            throw IOException("Failed to create directory for image.")
        }

        val imageFile = File.createTempFile(
            imageFileName, /* prefix */
            ".jpg",        /* suffix */
            storageDir     /* directory */
        )

        // Get the URI for the file using FileProvider
        return FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider", // Authority must match AndroidManifest
            imageFile
        )
    }

    // 🆕 Method to launch the camera
    private fun launchCamera() {
        try {
            tempImageUri = createImageFileUri() // Create a file URI for the camera to save to
            takePictureLauncher.launch(tempImageUri!!) // Pass the URI to the launcher
        } catch (ex: IOException) {
            // Error occurred while creating the File
            Toast.makeText(this, "Error creating image file: ${ex.message}", Toast.LENGTH_LONG).show()
            tempImageUri = null // Reset if URI creation failed
        }
    }
}