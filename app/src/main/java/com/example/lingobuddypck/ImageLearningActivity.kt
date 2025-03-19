package com.example.lingobuddypck

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.Network.TogetherAI.ChatImageResponse
import com.example.lingobuddypck.Network.TogetherAI.ChatRequestImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ImageLearningActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var inputMessage: EditText
    private lateinit var sendButton: Button
    private lateinit var responseTextView: TextView

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

        imageView = findViewById(R.id.imageView)
        inputMessage = findViewById(R.id.inputMessage)
        sendButton = findViewById(R.id.sendButton)
        responseTextView = findViewById(R.id.responseTextView)

        imageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        sendButton.setOnClickListener {
            sendImageAndMessageToAI()
        }
    }

    private fun sendImageAndMessageToAI() {
        val message = inputMessage.text.toString().trim()

        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select an image", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val base64Image = encodeImageToBase64(selectedImageUri!!)

                withContext(Dispatchers.Main) {
                    val contentList = mutableListOf<Map<String, Any>>()

                    if (message.isNotEmpty()) {
                        contentList.add(mapOf("type" to "text", "text" to message))
                    }
                    contentList.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to base64Image)))

                    val messages = listOf(mapOf("role" to "user", "content" to contentList))

                    val request = ChatRequestImage(
                        model = "meta-llama/Llama-Vision-Free",
                        messages = messages
                    )

                    RetrofitClient.instance.chatWithImageAI(request).enqueue(object : Callback<ChatImageResponse> {
                        override fun onResponse(call: Call<ChatImageResponse>, response: Response<ChatImageResponse>) {
                            if (response.isSuccessful) {
                                val aiResponse = response.body()?.choices?.get(0)?.message?.content
                                responseTextView.text = aiResponse ?: "No response from AI."
                            } else {
                                responseTextView.text = "Error: ${response.message()}"
                            }
                        }

                        override fun onFailure(call: Call<ChatImageResponse>, t: Throwable) {
                            responseTextView.text = "Error: ${t.message}"
                            Log.e("API_ERROR", "API call failed", t)
                        }
                    })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    responseTextView.text = "Error encoding image: ${e.message}"
                    Log.e("IMAGE_ENCODING", "Image encoding failed", e)
                }
            }
        }
    }

    private suspend fun encodeImageToBase64(imageUri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return@withContext "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
}