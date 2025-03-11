package com.example.lingobuddypck
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddy.Network.RetrofitClient
import com.example.lingobuddy.Network.TogetherAI.ChatRequest
import com.example.lingobuddy.Network.TogetherAI.ChatResponse
import com.example.lingobuddy.Network.TogetherAI.Message
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private val fullHistory = mutableListOf<Message>(
        Message("system", "Bạn là một trợ lý ảo vui vẻ và kiên nhẫn, giúp người học cải thiện tiếng Anh thông qua hội thoại tự nhiên. Tên: Lingo")
    )
    private val maxHistorySize = 10 // Gửi 10 tin gần nhất cho AI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val inputMessage = findViewById<EditText>(R.id.inputMessage)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val adapter = ChatAdapter(fullHistory);
        val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(ChatItemDecoration(50))
        sendButton.setOnClickListener {
            val message = inputMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessageToAI(message, adapter,recyclerView)
                inputMessage.text.clear()
            } else {
                Toast.makeText(this, "Vui lòng nhập tin nhắn", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendMessageToAI(message: String, adapter : ChatAdapter,recyclerView : RecyclerView) {
        fullHistory.add(Message("user", message)) // Lưu vào lịch sử đầy đủ

        val recentHistory = getRecentHistory() // Lấy 10 tin nhắn gần nhất để gửi AI
        val request = ChatRequest(messages = recentHistory)

        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful) {
                    val aiResponse = response.body()?.output?.choices?.get(0)?.text
                    if (aiResponse != null) {
                        adapter.notifyItemInserted(fullHistory.size - 1)
                        recyclerView.scrollToPosition(fullHistory.size - 1)
                        fullHistory.add(Message("assistant", aiResponse)) // Lưu phản hồi AI vào lịch sử
                    } else {
                        //responseText.text = "Không có phản hồi."
                    }
                } else {
                   // responseText.text = "Lỗi phản hồi từ AI."
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
               // responseText.text = "Lỗi: ${t.message}"
            }
        })
    }

    private fun getRecentHistory(): List<Message> {
        // Chỉ lấy 10 tin gần nhất (bao gồm cả "system" nếu có)
        return if (fullHistory.size > maxHistorySize) {
            fullHistory.takeLast(maxHistorySize)
        } else {
            fullHistory
        }
    }
}