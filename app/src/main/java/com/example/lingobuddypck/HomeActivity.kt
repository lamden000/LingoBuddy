package com.example.lingobuddypck

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.adapter.FeatureAdapter
import com.example.lingobuddypck.data.Feature

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val featureList = listOf(
            Feature("Chat với AI Gia Sư", R.drawable.chat_ai),
            Feature("Luyện phát âm bằng giọng nói", R.drawable.speech_practice),
            Feature("Nhận diện & Học từ vựng từ hình ảnh", R.drawable.image_recognition),
            Feature("Học từ vựng & Ngữ pháp qua đoạn văn AI tạo", R.drawable.grammar_learning),
            Feature("Nhập vai với AI", R.drawable.role_playing)
        )
        val featureActivities = mapOf(
            "Chat với AI Gia Sư" to ChatWithAIActivity::class.java,
            "Luyện phát âm bằng giọng nói" to PronunciationActivity::class.java,
            "Nhận diện & Học từ vựng từ hình ảnh" to ImageLearningActivity::class.java
        )

        val adapter = FeatureAdapter(featureList) { feature ->
            val activityClass = featureActivities[feature.name]
            if (activityClass != null) {
                val intent = Intent(this, activityClass)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Chức năng chưa được hỗ trợ!", Toast.LENGTH_SHORT).show()
            }
        }

        recyclerView.adapter = adapter
    }
}
