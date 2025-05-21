package com.example.lingobuddypck.ui.dictionary

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lingobuddypck.Services.AIGradingResult
import com.example.lingobuddypck.Services.QuestionData
import com.example.lingobuddypck.Services.UserAnswer
import com.example.lingobuddypck.Services.QuizService.AiQuizService
import com.example.lingobuddypck.Services.QuizService.NormalQuiz.QuizViewModel
import com.example.lingobuddypck.Repository.FirebaseWordRepository
import com.example.lingobuddypck.Repository.SavedWord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lớp Event để xử lý các sự kiện chỉ muốn quan sát một lần,
 * ví dụ như hiển thị Toast hoặc điều hướng.
 */
open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set // Cho phép đọc từ bên ngoài nhưng không cho phép ghi
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    fun peekContent(): T = content
}

class SavedWordsViewModel(
    private val aiQuizService: AiQuizService
)  : ViewModel(), QuizViewModel {

    // Khởi tạo Repository
    private val repository = FirebaseWordRepository()

    private val _testQuestions = MutableLiveData<List<QuestionData>?>()
    override val testQuestions: LiveData<List<QuestionData>?> = _testQuestions

    private val _gradingResult = MutableLiveData<AIGradingResult?>()
    override val gradingResult: LiveData<AIGradingResult?> = _gradingResult

    private val _errorMesssage = MutableLiveData<String?>()
    override val errorMessage: LiveData<String?> = _errorMesssage

    private val _isFetchingTest = MutableLiveData<Boolean>()
    override val isFetchingTest: LiveData<Boolean> = _isFetchingTest


    private val _savedWords = MutableLiveData<List<SavedWord>>()
    val savedWords: LiveData<List<SavedWord>> = _savedWords

    // LiveData cho trạng thái đang tải dữ liệu
    private val _isLoading = MutableLiveData<Boolean>()
    override val isLoading: LiveData<Boolean> = _isLoading

    // LiveData cho thông báo lỗi (sử dụng Event để chỉ hiển thị một lần)
    private val _error = MutableLiveData<Event<String?>>()
    val error: LiveData<Event<String?>> = _error

    // LiveData cho kết quả của các thao tác (ví dụ: thông báo thành công/thất bại khi xóa, sửa)
    private val _operationResult = MutableLiveData<Event<String>>()
    val operationResult: LiveData<Event<String>> = _operationResult

    class Factory(
        private val aiQuizService: AiQuizService
        // Add other dependencies needed by TestViewModel constructor here
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SavedWordsViewModel::class.java)) {
                return SavedWordsViewModel(aiQuizService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
    override fun fetchTest(topic: String, isCustom: Boolean) {
        _isLoading.value = true
        _testQuestions.value = null
        _gradingResult.value = null
        _errorMesssage.value = null
        _isFetchingTest.value = true

        viewModelScope.launch {
            val questionResult = tryFetchQuizOnce()
            if (questionResult == null) {
                delay(5000) // Wait 5 seconds before retry
                val retryResult = tryFetchQuizOnce()
                if (retryResult == null) {
                    _errorMesssage.postValue("Lỗi khi tạo đề bài. Vui lòng thử lại sau.")
                } else {
                    _testQuestions.postValue(retryResult)
                }
            } else {
                _testQuestions.postValue(questionResult)
            }
            _isLoading.postValue(false)
            _isFetchingTest.postValue(false)
        }
    }

    private suspend fun tryFetchQuizOnce(): List<QuestionData>? {
        return try {
            aiQuizService.generateQuiz(
                "Tạo đề bài (đảm bảo đủ 10 câu hỏi) gồm các từ: " + getRandomSavedWordsString(),
                true
            )
        } catch (e: Exception) {
            Log.e("TestViewModel", "Error fetching test", e)
            null
        }
    }

    override fun submitAnswers(userAnswers: List<UserAnswer>) {
        val currentQuestions = _testQuestions.value
        if (currentQuestions == null || currentQuestions.isEmpty()) {
            _errorMesssage.value = "Không có bài test để chấm điểm."
            return
        }

        // ViewModel updates its own loading state
        _isLoading.value = true
        _gradingResult.value = null
        _errorMesssage.value = null

        viewModelScope.launch {
            try {
                // Call the service function
                val result = aiQuizService.gradeQuiz(currentQuestions, userAnswers)
                // Update ViewModel LiveData with the result
                _gradingResult.postValue(result)
            } catch (e: Exception) {
                // Handle exceptions thrown by the service
                Log.e("TestViewModel", "Error submitting answers", e) // Log the error
                _errorMesssage.postValue(e.message)
            } finally {
                // ViewModel updates its own loading state
                _isLoading.postValue(false)
            }
        }
    }

    // These remain as they manage ViewModel-specific state
    override  fun clearGradingResult() {
        _gradingResult.value = null
    }

    override  fun clearErrorMessage() {
        _errorMesssage.value = null
    }

    override fun clearQuestions() {
        _testQuestions.value= null
    }

    init {
        // Tải danh sách từ ngay khi ViewModel được tạo
        loadSavedWords()
    }

    /**
     * Yêu cầu Repository tải danh sách các từ đã lưu.
     * Cập nhật _isLoading và _savedWords hoặc _error dựa trên kết quả.
     */
    fun loadSavedWords() {
        repository.getSavedWords { result ->
            result.onSuccess { words ->
                _savedWords.value = words
            }.onFailure { exception ->
                _error.value = Event("Lỗi tải từ: ${exception.message}")
            }
        }
    }

    /**
     * Yêu cầu Repository xóa một từ dựa trên wordId.
     * Cập nhật _isLoading và _operationResult.
     */
    fun deleteWord(wordId: String) {
        if (wordId.isBlank()) {
            _operationResult.value = Event("ID của từ không hợp lệ.")
            return
        }
        repository.deleteWord(
            wordId = wordId,
            onSuccess = {
                _operationResult.value = Event("Đã xóa từ thành công.")
                // Danh sách LiveData _savedWords sẽ tự động cập nhật
                // nếu getSavedWords trong repository sử dụng addSnapshotListener.
                // Nếu không, bạn có thể cần gọi lại loadSavedWords() ở đây.
            },
            onFailure = { exception ->
                _operationResult.value = Event("Lỗi khi xóa từ: ${exception.message}")
            }
        )
    }

    /**
     * Yêu cầu Repository cập nhật một từ.
     * Cập nhật _isLoading và _operationResult.
     */
    fun updateWord(wordId: String, newWord: String, newNote: String) {
        if (wordId.isBlank()) {
            _operationResult.value = Event("ID của từ không hợp lệ để cập nhật.")
            return
        }
        if (newWord.isBlank()) {
            _operationResult.value = Event("Từ không được để trống.")
            return
        }
        repository.updateWord(
            wordId = wordId,
            newWord = newWord,
            newNote = newNote,
            onSuccess = {
                _operationResult.value = Event("Đã cập nhật từ thành công.")
            },
            onFailure = { exception ->
                _operationResult.value = Event("Lỗi khi cập nhật từ: ${exception.message}")
            }
        )
    }
    fun getRandomSavedWordsString(): String {
        val words = _savedWords.value ?: return ""
        return words
            .shuffled()
            .take(10)
            .joinToString(separator = ", ") { it.word }
    }
}