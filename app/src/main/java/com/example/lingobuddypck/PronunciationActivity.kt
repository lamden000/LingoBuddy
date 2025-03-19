package com.example.lingobuddypck

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.Network.TogetherAI.ChatRequest
import com.example.lingobuddypck.Network.TogetherAI.ChatResponse
import com.example.lingobuddypck.Network.TogetherAI.Message
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PronunciationActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var btnStart: Button
    private lateinit var txtResult: TextView
    private lateinit var tvReference: TextView
    private lateinit var txtStatus: TextView
    private val referenceText = "Hello, how are you today?"
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAudioPermission()

        setContentView(R.layout.activity_pronunciation)
        btnStart = findViewById(R.id.btnStart)
        txtResult = findViewById(R.id.txtResult)
        tvReference=findViewById(R.id.tvReferenceText)
        txtStatus=findViewById(R.id.txtStatus)

        tvReference.text =referenceText;
        btnStart.setOnClickListener {
            startSpeechRecognition()
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }
    }

    private fun startSpeechRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val userSpeech = matches[0]
                    txtResult.text = "Bạn đã nói: $userSpeech"
                    checkPronunciation(userSpeech, referenceText)
                }
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Lỗi mạng: Hết thời gian chờ"
                    SpeechRecognizer.ERROR_NETWORK -> "Lỗi mạng: Không kết nối được"
                    SpeechRecognizer.ERROR_AUDIO -> "Lỗi âm thanh"
                    SpeechRecognizer.ERROR_SERVER -> "Lỗi máy chủ"
                    SpeechRecognizer.ERROR_CLIENT -> "Lỗi ứng dụng"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nhận diện được giọng nói"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Không tìm thấy kết quả"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Bộ nhận diện đang bận"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Thiếu quyền RECORD_AUDIO"
                    else -> "Lỗi không xác định: $error"
                }

                Log.e("SpeechRecognizer", errorMessage)
                Toast.makeText(this@PronunciationActivity, errorMessage, Toast.LENGTH_SHORT).show()
            }

            override fun onReadyForSpeech(params: Bundle?) {
                    txtStatus.text="Đang nghe..."
            }

            override fun onBeginningOfSpeech() {
                txtStatus.text="Đã nhận diện giọng nói..."
            }

            override fun onEndOfSpeech() {
                txtStatus.text="Ghi âm kết thúc."
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }
    private fun checkPronunciation(userSpeech: String, referenceText: String) {
        val prompt = """
        Tôi sẽ đưa cho bạn một câu tiếng Anh và một câu được đọc bởi người học. 
        Hãy chấm điểm phát âm (từ 0-10) và chỉ ra lỗi sai.
        Sau đó, gợi ý cách sửa lỗi (không xét các dấu câu vì đây là phát âm).
        
        **Câu gốc:** "$referenceText"
        **Người học nói:** "$userSpeech"
        
        Trả lời dạng JSON:
        {
          "score": 8.5,
          "mistakes": ["word1", "word2"],
          "suggestions": ["Phát âm lại 'word1' với âm /æ/ thay vì /e/"]
        }
    """.trimIndent()

        val request = ChatRequest(model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free", messages = listOf(
            Message("user", prompt)
        ))

        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                val result = response.body()?.output?.choices?.get(0)?.text?: "Không có phản hồi từ AI"
                showCorrectionResult(result)
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                Toast.makeText(this@PronunciationActivity, "Lỗi khi gọi AI", Toast.LENGTH_SHORT).show()
            }
        })
    }
    private fun showCorrectionResult(result: String) {
        val json = JSONObject(result)
        val score = json.getDouble("score")
        val mistakes = json.getJSONArray("mistakes")
        val suggestions = json.getJSONArray("suggestions")

        val feedback = "Điểm phát âm: $score/10\nLỗi: ${mistakes.join(", ")}\nGợi ý sửa: ${suggestions.join("\n")}"

        AlertDialog.Builder(this)
            .setTitle("Kết quả chấm điểm")
            .setMessage(feedback)
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

}