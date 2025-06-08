package com.example.lingobuddypck.utils

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import com.example.lingobuddypck.data.Task
import android.widget.Toast

object TaskManager {
    private const val PREF_NAME = "daily_tasks"
    private const val KEY_TASK_COMPLETED = "task_completed_"
    private const val KEY_DAILY_TASKS = "daily_tasks_"
    private const val KEY_DAILY_TOPIC = "daily_topic_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun getTodayString(): String {
        return LocalDate.now().toString()
    }

    // Task Types
    enum class TaskType {
        PRONUNCIATION_SCORE,
        PRONUNCIATION_TOPIC,
        VOCABULARY,
        GRAMMAR,
        LISTENING,
        IMAGE_QUIZ_SCORE,
        IMAGE_SEND_TWO
        // Add more task types as needed
    }

    data class DailyTask(
        val type: TaskType,
        val createTask: (Context) -> Task
    )

    // Available tasks pool
    private val availableTasks = mutableMapOf<TaskType, DailyTask>()

    fun registerTask(taskType: TaskType, taskCreator: DailyTask) {
        availableTasks[taskType] = taskCreator
    }

    fun initializeDefaultTasks() {

        // Image Quiz Score Task
        registerTask(
            TaskType.IMAGE_QUIZ_SCORE,
            DailyTask(TaskType.IMAGE_QUIZ_SCORE) { context ->
                Task("📸 Hoàn thành một bài quiz với hình ảnh và đạt trên 8 điểm") {
                    // Action will be set by the Fragment/Activity
                }
            }
        )

        // Image Send Two Task
        registerTask(
            TaskType.IMAGE_SEND_TWO,
            DailyTask(TaskType.IMAGE_SEND_TWO) { context ->
                Task("🖼️ Gửi 2 hình ảnh cho AI") {
                    // Action will be set by the Fragment/Activity
                }
            }
        )

        // Pronunciation Score Task
        registerTask(
            TaskType.PRONUNCIATION_SCORE,
            DailyTask(TaskType.PRONUNCIATION_SCORE) { context ->
                Task("🎯 Thực hiện một bài luyện phát âm và đạt trên 8 điểm") {
                    // Action will be set by the Fragment/Activity
                }
            }
        )

        // Pronunciation Topic Task
        registerTask(
            TaskType.PRONUNCIATION_TOPIC,
            DailyTask(TaskType.PRONUNCIATION_TOPIC) { context ->
                Task("🗣️ Luyện phát âm một câu thuộc chủ đề: ${getDailyTopic(context)}") {
                    // Action will be set by the Fragment/Activity
                }
            }
        )

        // Add more default tasks here
    }

    fun getDailyTopic(context: Context): String {
        val prefs = getPrefs(context)
        val today = getTodayString()
        var topic = prefs.getString(KEY_DAILY_TOPIC + today, null)
        
        if (topic == null) {
            topic = TopicUtils.getRandomTopicFromAssets(context)
            prefs.edit().putString(KEY_DAILY_TOPIC + today, topic).apply()
        }
        
        return topic
    }

    fun isTaskCompleted(context: Context, taskType: TaskType): Boolean {
        val prefs = getPrefs(context)
        val today = getTodayString()
        return prefs.getBoolean(KEY_TASK_COMPLETED + today + "_" + taskType.name, false)
    }

    fun isTaskInToday(context: Context, taskType: TaskType): Boolean {
        val prefs = getPrefs(context)
        val today = getTodayString()
        val savedTaskTypes = prefs.getStringSet(KEY_DAILY_TASKS + today, null)
        return savedTaskTypes?.contains(taskType.name) == true
    }

    fun markTaskCompleted(context: Context, taskType: TaskType) {
        val prefs = getPrefs(context)
        val today = getTodayString()
        
        // Only mark and notify if it's in today's tasks and not already completed
        if (isTaskInToday(context, taskType) && !isTaskCompleted(context, taskType)) {
            prefs.edit().putBoolean(KEY_TASK_COMPLETED + today + "_" + taskType.name, true).apply()
            
            // Show completion notification
            val taskName = when (taskType) {
                TaskType.PRONUNCIATION_SCORE -> "Luyện phát âm đạt trên 8 điểm"
                TaskType.PRONUNCIATION_TOPIC -> "Luyện phát âm theo chủ đề"
                TaskType.IMAGE_QUIZ_SCORE -> "Quiz hình ảnh đạt trên 8 điểm"
                TaskType.IMAGE_SEND_TWO -> "Gửi 2 hình ảnh cho AI"
                else -> "Nhiệm vụ"
            }
            
            Toast.makeText(
                context,
                "Bạn đã hoàn thành nhiệm vụ: $taskName",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun getDailyTasks(context: Context, numberOfTasks: Int = 2): List<Task> {
        val prefs = getPrefs(context)
        val today = getTodayString()

        // Check if we already have tasks for today
        val savedTaskTypes = prefs.getStringSet(KEY_DAILY_TASKS + today, null)
        
        if (savedTaskTypes != null) {
            return savedTaskTypes.mapNotNull { taskTypeName ->
                try {
                    val taskType = TaskType.valueOf(taskTypeName)
                    availableTasks[taskType]?.createTask?.invoke(context)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }

        // If no tasks for today, randomly select new ones
        val selectedTasks = availableTasks.values.shuffled().take(numberOfTasks)
        
        // Save selected task types for today
        prefs.edit().putStringSet(
            KEY_DAILY_TASKS + today,
            selectedTasks.map { it.type.name }.toSet()
        ).apply()

        return selectedTasks.map { it.createTask(context) }
    }

    fun clearTasksForTesting(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().clear().apply()
    }
} 