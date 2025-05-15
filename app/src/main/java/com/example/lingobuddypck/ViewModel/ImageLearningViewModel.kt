package com.example.lingobuddypck.ViewModel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.Network.TogetherAI.ChatImageResponse
import com.example.lingobuddypck.Network.TogetherAI.ChatRequestImage
import com.example.lingobuddypck.Network.TogetherAI.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ImageLearningViewModel : ViewModel() {

    private val _chatMessages = MutableLiveData<List<Message>>()
    val chatMessages: LiveData<List<Message>> = _chatMessages

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    fun sendImageAndMessage(context: Context, message: String, imageUri: Uri?) {
        _loading.value = true

        viewModelScope.launch {
            try {
                val contentList = mutableListOf<Map<String, Any>>()

                // Add user message
                if (message.isNotEmpty()) {
                    contentList.add(mapOf("type" to "text", "text" to message))
                }

                // Add image message if URI is present
                if (imageUri != null) {
                    val base64Image = encodeImageToBase64(context, imageUri)
                    contentList.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to base64Image)))
                }

                // Prepare the messages for the request
                val messages = listOf(mapOf("role" to "user", "content" to contentList))
                val request = ChatRequestImage(
                    model = "meta-llama/Llama-Vision-Free",
                    messages = messages
                )

                // Call the API
                RetrofitClient.instance.chatWithImageAI(request).enqueue(object : Callback<ChatImageResponse> {
                    override fun onResponse(call: Call<ChatImageResponse>, response: Response<ChatImageResponse>) {
                        _loading.value = false
                        if (response.isSuccessful) {
                            val responseContent = response.body()?.choices?.get(0)?.message?.content ?: "No response from AI."

                            // Prepare AI's response message
                            val aiMessage = Message(
                                content = responseContent,
                                role = "AI",
                                imageUri = null // AI response will contain text only
                            )

                            // Prepare the user message (could contain text and image URI)
                            val userMessage = Message(
                                content = message,
                                role = "user",
                                imageUri = imageUri // User message can contain image URI
                            )

                            // Update the LiveData with the new list of messages
                            val currentMessages = _chatMessages.value.orEmpty().toMutableList()
                            currentMessages.add(userMessage)
                            currentMessages.add(aiMessage)

                            _chatMessages.value = currentMessages
                        } else {
                            _loading.value = false
                            _chatMessages.value = listOf(Message("Error: ${response.message()}", "ai"))
                        }
                    }

                    override fun onFailure(call: Call<ChatImageResponse>, t: Throwable) {
                        _loading.value = false
                        _chatMessages.value = listOf(Message("Ai", "Error: ${t.message}"))
                        Log.e("API_ERROR", "API call failed", t)
                    }
                })
            } catch (e: Exception) {
                _loading.value = false
                _chatMessages.value = listOf(Message("Error encoding image: ${e.message}", "Ai"))
                Log.e("IMAGE_ENCODING", "Image encoding failed", e)
            }
        }
    }

    private suspend fun encodeImageToBase64(context: Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return@withContext "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
}
