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

    private val chatHistory = mutableListOf<Message>()
    private val MAX_CHAT_HISTORY = 5

    fun sendImageAndMessage(context: Context, message: String, imageUri: Uri?) {
        _loading.value = true

        viewModelScope.launch { // This is your coroutine scope
            try {
                val contentList = mutableListOf<Map<String, Any>>()

                // Add user message
                if (message.isNotEmpty()) {
                    contentList.add(mapOf("type" to "text", "text" to message))
                }

                var base64ImageForUserMessage: String? = null
                // Perform the image encoding here, within the coroutine scope
                if (imageUri != null) {
                    base64ImageForUserMessage = encodeImageToBase64(context, imageUri)
                    contentList.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to base64ImageForUserMessage)))
                }

                // Prepare the messages for the request, including chat history
                val messagesForRequest = mutableListOf<Map<String, Any>>()

                // Add past messages from history to the request
                chatHistory.forEach { msg ->
                    val historyContentList = mutableListOf<Map<String, Any>>()
                    msg.content?.let {
                        historyContentList.add(mapOf("type" to "text", "text" to it))
                    }
                    // IMPORTANT: Ensure imageUrl is available in your Message data class
                    // and holds the base64 string for historical images.
                    msg.imageUrl?.let {
                        historyContentList.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to it)))
                    }
                    messagesForRequest.add(mapOf("role" to msg.role, "content" to historyContentList))
                }

                // Add the current user message to the request
                messagesForRequest.add(mapOf("role" to "user", "content" to contentList))

                val request = ChatRequestImage(
                    model = "meta-llama/Llama-Vision-Free",
                    messages = messagesForRequest
                )

                // Call the API (using enqueue for Retrofit, which handles its own threading)
                RetrofitClient.instance.chatWithImageAI(request).enqueue(object : Callback<ChatImageResponse> {
                    override fun onResponse(call: Call<ChatImageResponse>, response: Response<ChatImageResponse>) {
                        _loading.value = false
                        if (response.isSuccessful) {
                            val responseContent = response.body()?.choices?.get(0)?.message?.content ?: "No response from AI."

                            // Prepare AI's response message
                            val aiMessage = Message(
                                content = responseContent,
                                role = "AI",
                                imageUri = null
                            )

                            // Prepare the user message (now using the pre-encoded base64 string)
                            val userMessage = Message(
                                content = message,
                                role = "user",
                                imageUri = imageUri,
                                imageUrl = base64ImageForUserMessage // Use the already encoded base64 string
                            )

                            // Update the LiveData with the new list of messages
                            val currentMessages = _chatMessages.value.orEmpty().toMutableList()
                            currentMessages.add(userMessage)
                            currentMessages.add(aiMessage)
                            _chatMessages.value = currentMessages

                            // Update chat history
                            chatHistory.add(userMessage)
                            chatHistory.add(aiMessage)

                            // Trim history
                            if (chatHistory.size > MAX_CHAT_HISTORY * 2) {
                                chatHistory.subList(0, chatHistory.size - MAX_CHAT_HISTORY * 2).clear()
                            }

                        } else {
                            _loading.value = false
                            _chatMessages.value = listOf(Message("Error: ${response.message()}", "AI"))
                        }
                    }

                    override fun onFailure(call: Call<ChatImageResponse>, t: Throwable) {
                        _loading.value = false
                        _chatMessages.value = listOf(Message("AI", "Error: ${t.message}"))
                        Log.e("API_ERROR", "API call failed", t)
                    }
                })
            } catch (e: Exception) {
                _loading.value = false
                _chatMessages.value = listOf(Message("Error encoding image: ${e.message}", "AI"))
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