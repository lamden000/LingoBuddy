package com.example.lingobuddypck.ui.dictionary

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lingobuddypck.Network.TogetherAI.AIGradingResult
import com.example.lingobuddypck.Network.TogetherAI.QuestionData
import com.example.lingobuddypck.Network.TogetherAI.UserAnswer
import com.example.lingobuddypck.ViewModel.Repository.AiQuizService
import com.example.lingobuddypck.ViewModel.Repository.FirebaseWordRepository
import com.example.lingobuddypck.ViewModel.Repository.SavedWord
import com.example.lingobuddypck.ViewModel.TestViewModel
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
)  : ViewModel() {

    // Khởi tạo Repository
    private val repository = FirebaseWordRepository()

    private val _testQuestions = MutableLiveData<List<QuestionData>?>()
    val testQuestions: LiveData<List<QuestionData>?> = _testQuestions

    private val _gradingResult = MutableLiveData<AIGradingResult?>()
    val gradingResult: LiveData<AIGradingResult?> = _gradingResult

    private val _errorMesssage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMesssage

    private val _savedWords = MutableLiveData<List<SavedWord>>()
    val savedWords: LiveData<List<SavedWord>> = _savedWords

    // LiveData cho trạng thái đang tải dữ liệu
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

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
    fun fetchTest() {
        // ViewModel updates its own loading state
        _isLoading.value = true
        _testQuestions.value = null // Clear previous state
        _gradingResult.value = null
        _errorMesssage.value = null

        viewModelScope.launch {
            try {
                // Call the service function
                val questions = aiQuizService.generateQuiz("Tạo đề bài (đảm bảo đủ 10 câu hỏi) gồm các từ: "+getRandomSavedWordsString(), true)
                // Update ViewModel LiveData with the result
                _testQuestions.postValue(questions)
            } catch (e: Exception) {
                // Handle exceptions thrown by the service
                Log.e("TestViewModel", "Error fetching test", e) // Log the error
                _errorMesssage.postValue(e.message)
            } finally {
                // ViewModel updates its own loading state
                _isLoading.postValue(false)
            }
        }
    }

    fun submitAnswers(userAnswers: List<UserAnswer>) {
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
    fun clearGradingResult() {
        _gradingResult.value = null
    }

    fun clearErrorMessage() {
        _errorMesssage.value = null
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
        _isLoading.value = true
        // Giả sử getSavedWords trong repository của bạn nhận một lambda để xử lý kết quả
        // (như trong ví dụ FirebaseWordRepository chúng ta đã tạo trước đó)
        repository.getSavedWords { result ->
            _isLoading.value = false // Dừng trạng thái tải dù thành công hay thất bại
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
        _isLoading.value = true
        repository.deleteWord(
            wordId = wordId,
            onSuccess = {
                _isLoading.value = false
                _operationResult.value = Event("Đã xóa từ thành công.")
                // Danh sách LiveData _savedWords sẽ tự động cập nhật
                // nếu getSavedWords trong repository sử dụng addSnapshotListener.
                // Nếu không, bạn có thể cần gọi lại loadSavedWords() ở đây.
            },
            onFailure = { exception ->
                _isLoading.value = false
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
        _isLoading.value = true
        repository.updateWord(
            wordId = wordId,
            newWord = newWord,
            newNote = newNote,
            onSuccess = {
                _isLoading.value = false
                _operationResult.value = Event("Đã cập nhật từ thành công.")
                // Tương tự như deleteWord, _savedWords sẽ tự cập nhật nếu dùng SnapshotListener.
            },
            onFailure = { exception ->
                _isLoading.value = false
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