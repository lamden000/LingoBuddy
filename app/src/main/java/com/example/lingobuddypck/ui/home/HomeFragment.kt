package com.example.lingobuddypck.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.ui.ChatWithAIActivity
import com.example.lingobuddypck.ui.ImageLearningActivity
import com.example.lingobuddypck.ui.PassageQuizActivity
import com.example.lingobuddypck.ui.PronunciationActivity
import com.example.lingobuddypck.R
import com.example.lingobuddypck.ui.RolePlayActivity
import com.example.lingobuddypck.ui.TestActivity
import com.example.lingobuddypck.adapter.FeatureAdapter
import com.example.lingobuddypck.data.Feature
import com.example.lingobuddypck.data.Task

class HomeFragment : Fragment() {

    private lateinit var buttonStartTest:Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonStartTest=view.findViewById(R.id.buttonTest)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val taskLayout = view.findViewById<LinearLayout>(R.id.dailyTaskLayout)
        val taskButton = view.findViewById<Button>(R.id.buttonDailyTask)

        taskButton.setOnClickListener {
            taskLayout.visibility = if (taskLayout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        val featureList = listOf(
            Feature("Chat với AI Gia Sư", R.drawable.chat_ai),
            Feature("Luyện phát âm bằng giọng nói", R.drawable.ic_pronounciation),
            Feature("Nhận diện & Học từ vựng từ hình ảnh", R.drawable.ic_camera),
            Feature("Học từ vựng & Ngữ pháp qua đoạn văn AI tạo", R.drawable.ic_test),
            Feature("Nhập vai với AI", R.drawable.ic_role_play)
        )

        val featureActivities = mapOf(
            "Chat với AI Gia Sư" to ChatWithAIActivity::class.java,
            "Luyện phát âm bằng giọng nói" to PronunciationActivity::class.java,
            "Nhận diện & Học từ vựng từ hình ảnh" to ImageLearningActivity::class.java,
            "Nhập vai với AI" to RolePlayActivity::class.java,
            "Học từ vựng & Ngữ pháp qua đoạn văn AI tạo" to PassageQuizActivity::class.java
        )

        buttonStartTest.setOnClickListener {
            val intent = Intent(requireActivity(), TestActivity::class.java)
            startActivity(intent)
        }

        val adapter = FeatureAdapter(featureList) { feature ->
            val activityClass = featureActivities[feature.name]
            if (activityClass != null) {
                val intent = Intent(requireContext(), activityClass)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Chức năng chưa được hỗ trợ!", Toast.LENGTH_SHORT).show()
            }
        }

        addDailyTasks(
            listOf(
                Task("🧠 Ôn từ vựng hôm nay") {  },
                Task("📖 Làm bài tập ngữ pháp") {  },
                Task("🎧 Nghe hội thoại mẫu") {  }
            )
        )

        recyclerView.adapter = adapter
    }

    private fun addDailyTasks(tasks: List<Task>) {
        val container = view?.findViewById<LinearLayout>(R.id.dailyTaskLayout) ?: return
        container.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())

        for (task in tasks) {
            val itemView = inflater.inflate(R.layout.item_daily_task, container, false)
            val textView = itemView.findViewById<TextView>(R.id.textTaskName)
            val button = itemView.findViewById<Button>(R.id.buttonGo)

            textView.text = task.name
            button.setOnClickListener { task.action() }

            container.addView(itemView)
        }
    }
}
