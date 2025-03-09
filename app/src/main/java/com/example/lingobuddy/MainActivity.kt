package com.example.lingobuddypck
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lingobuddy.Network.RetrofitClient
import com.example.lingobuddy.Network.TogetherAI.ChatRequest
import com.example.lingobuddy.Network.TogetherAI.ChatResponse
import com.example.lingobuddy.Network.TogetherAI.Message
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private val conversationHistory = mutableListOf<Message>(
        Message("system", "Bạn là một trợ lý ảo vui vẻ và kiên nhẫn, giúp người học cải thiện tiếng Anh thông qua hội thoại tự nhiên.Tên: Lingo")
    )
    private val maxHistorySize = 10 // Store the 10 latest messages

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val inputMessage = findViewById<EditText>(R.id.inputMessage)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val responseText = findViewById<TextView>(R.id.responseText)

        sendButton.setOnClickListener {
            val message = inputMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessageToAI(message, responseText)
            } else {
                Toast.makeText(this, "Vui lòng nhập tin nhắn", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendMessageToAI(message: String, responseText: TextView) {
        conversationHistory.add(Message("user", message))
        enforceMaxHistorySize() // Ensure history doesn't exceed the limit

        val request = ChatRequest(messages = conversationHistory)

        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful) {
                    val aiResponse = response.body()?.output?.choices?.get(0)?.text
                    if (aiResponse != null) {
                        responseText.text = aiResponse
                        conversationHistory.add(Message("assistant", aiResponse))
                        enforceMaxHistorySize() // Ensure history doesn't exceed the limit after AI response
                    } else {
                        responseText.text = "Không có phản hồi."
                    }
                } else {
                    responseText.text = "Lỗi phản hồi từ AI."
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                responseText.text = "Lỗi: ${t.message}"
            }
        })
    }

    private fun enforceMaxHistorySize() {
        while (conversationHistory.size > maxHistorySize) {
            conversationHistory.removeAt(1) // Remove the oldest user or assistant message, keep system message
        }
    }
}