package com.example.lingobuddypck.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.lingobuddypck.ViewModel.Repository.FirebaseWordRepository
import com.example.lingobuddypck.ViewModel.Repository.SavedWord

/**
 * Lớp Event để xử lý các sự kiện chỉ muốn quan sát một lần,
 * ví dụ như hiển thị Toast hoặc điều hướng.
 */
open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set // Cho phép đọc từ bên ngoài nhưng không cho phép ghi

    /**
     * Trả về nội dung và đánh dấu là đã được xử lý.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * Trả về nội dung, ngay cả khi nó đã được xử lý.
     * Hữu ích cho việc xem trước dữ liệu mà không tiêu thụ nó.
     */
    fun peekContent(): T = content
}

class SavedWordsViewModel : ViewModel() {

    // Khởi tạo Repository
    private val repository = FirebaseWordRepository()

    // LiveData cho danh sách các từ đã lưu
    // _savedWords là MutableLiveData để ViewModel có thể cập nhật giá trị.
    // savedWords là LiveData (không thể thay đổi từ bên ngoài ViewModel) để Fragment quan sát.
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
}