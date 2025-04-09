package com.example.lingobuddypck.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.ChatWithAIActivity
import com.example.lingobuddypck.ImageLearningActivity
import com.example.lingobuddypck.PronunciationActivity
import com.example.lingobuddypck.R
import com.example.lingobuddypck.adapter.FeatureAdapter
import com.example.lingobuddypck.data.Feature

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

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
                val intent = Intent(requireContext(), activityClass)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Chức năng chưa được hỗ trợ!", Toast.LENGTH_SHORT).show()
            }
        }

        recyclerView.adapter = adapter
    }
}
