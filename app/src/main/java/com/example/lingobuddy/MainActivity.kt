package com.example.lingobuddy
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
    /*  private val apiKey = "Actual API key" // Thay thế bằng API Key của bạn
    private lateinit var editText: EditText
    private lateinit var button: Button
    private lateinit var resultTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        button = findViewById(R.id.button)
        resultTextView = findViewById(R.id.resultTextView)

        button.setOnClickListener {
            val text = editText.text.toString()
            analyzeSentiment(text)
        }
    }

    private fun analyzeSentiment(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = AnalyzeSentimentRequest(Document(text))
                val response = RetrofitClient.instance.analyzeSentiment(apiKey, request)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val sentiment = response.body()?.documentSentiment
                        val result = "Score: ${sentiment?.score}, Magnitude: ${sentiment?.magnitude}"
                        resultTextView.text = result
                        Log.d("Sentiment", result)
                    } else {
                        val error = "Error: ${response.errorBody()?.string()}"
                        resultTextView.text = error
                        Log.e("Sentiment", error)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val error = "Exception: ${e.message}"
                    resultTextView.text = error
                    Log.e("Sentiment", error)
                }
            }
        }
    }*/
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
        val request = ChatRequest(
            messages = listOf(
                Message("system", "Bạn là một trợ lý AI."),
                Message("user", message)
            )
        )

        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful) {
                    val aiResponse = response.body()?.output?.choices?.get(0)?.text
                    responseText.text = aiResponse ?: "Không có phản hồi."
                } else {
                    responseText.text = "Lỗi phản hồi từ AI."
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                responseText.text = "Lỗi: ${t.message}"
            }
        })
    }
}