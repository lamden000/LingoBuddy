package com.example.lingobuddypck.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions


import android.util.Log
import com.example.lingobuddypck.data.UserProfileBundle

class NotificationsViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // LiveData cho điểm và rank (giữ nguyên từ code của bạn)
    private val _userScoreText = MutableLiveData<String?>()
    val userScoreText: LiveData<String?> = _userScoreText

    private val _userRankText = MutableLiveData<String?>()
    val userRankText: LiveData<String?> = _userRankText

    // LiveData cho thông tin cá nhân (cập nhật để bao gồm aiChatTone)
    private val _fetchedUserInfo = MutableLiveData<UserProfileBundle?>() // Sử dụng UserProfileBundle
    val fetchedUserInfo: LiveData<UserProfileBundle?> = _fetchedUserInfo

    private val _isFetchingDetails = MutableLiveData<Boolean>() // Dùng chung cho fetch user info & tone
    val isFetchingDetails: LiveData<Boolean> = _isFetchingDetails

    // LiveData cho việc lưu thông tin cá nhân (tên, job,...)
    private val _isSavingPersonalInfo = MutableLiveData<Boolean>()
    val isSavingPersonalInfo: LiveData<Boolean> = _isSavingPersonalInfo // Đổi tên từ isSaving

    private val _personalInfoSaveSuccess = MutableLiveData<Boolean>()
    val personalInfoSaveSuccess: LiveData<Boolean> = _personalInfoSaveSuccess // Đổi tên từ saveSuccess

    // LiveData MỚI cho việc lưu AI Tone
    private val _isSavingAiTone = MutableLiveData<Boolean>()
    val isSavingAiTone: LiveData<Boolean> = _isSavingAiTone

    private val _aiToneSaveSuccess = MutableLiveData<Boolean>()
    val aiToneSaveSuccess: LiveData<Boolean> = _aiToneSaveSuccess

    // LiveData cho lỗi chung
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // --- Các hàm fetch hiện tại của bạn ---
    fun fetchUserProficiencyData() {
        _isFetchingDetails.value = true // Ví dụ: bạn có thể muốn set loading ở đây
        // TODO: Implement your logic to fetch proficiency score and rank
        // Ví dụ:
        // viewModelScope.launch {
        //      // Giả lập fetch
        //      kotlinx.coroutines.delay(1000)
        //      _userScoreText.postValue("90/100")
        //      _userRankText.postValue("Chuyên Gia")
        //      _isFetchingDetails.postValue(false) // Kết thúc loading nếu chỉ fetch điểm ở đây
        // }
        Log.d("NotificationsViewModel", "fetchUserProficiencyData called")
    }

    fun fetchCurrentUserInfo() { // Hàm này sẽ fetch cả personal info và aiChatTone
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _errorMessage.value = "Người dùng chưa đăng nhập."
            _fetchedUserInfo.value = null
            return
        }
        _isFetchingDetails.value = true
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val userInfo = UserProfileBundle(
                        name = document.getString("name"),
                        job = document.getString("job"),
                        interest = document.getString("interest"),
                        otherInfo = document.getString("otherInfo"),
                        aiChatTone = document.getString("aiChatTone") ?: "trung lập và thân thiện" // Mặc định nếu null
                    )
                    _fetchedUserInfo.value = userInfo
                } else {
                    // Document không tồn tại, trả về giá trị mặc định cho aiChatTone
                    _fetchedUserInfo.value = UserProfileBundle(aiChatTone = "trung lập và thân thiện")
                }
                _isFetchingDetails.value = false
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Lỗi khi tải thông tin người dùng: ${e.message}"
                _fetchedUserInfo.value = null // Hoặc UserProfileBundle với giá trị mặc định
                _isFetchingDetails.value = false
            }
    }

    // --- Các hàm save hiện tại của bạn ---
    fun savePersonalInfo(name: String?, job: String?, interest: String?, otherInfo: String?) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _errorMessage.value = "Người dùng chưa đăng nhập."
            return
        }
        _isSavingPersonalInfo.value = true
        _personalInfoSaveSuccess.value = false

        val personalInfoMap = mutableMapOf<String, Any>()
        if (!name.isNullOrBlank()) personalInfoMap["name"] = name else personalInfoMap["name"] = FieldValue.delete()
        if (!job.isNullOrBlank()) personalInfoMap["job"] = job else personalInfoMap["job"] = FieldValue.delete()
        if (!interest.isNullOrBlank()) personalInfoMap["interest"] = interest else personalInfoMap["interest"] = FieldValue.delete()
        if (!otherInfo.isNullOrBlank()) personalInfoMap["otherInfo"] = otherInfo else personalInfoMap["otherInfo"] = FieldValue.delete()

        if (personalInfoMap.isNotEmpty() || name.isNullOrBlank() || job.isNullOrBlank() || interest.isNullOrBlank() || otherInfo.isNullOrBlank() ) {
            // Luôn cập nhật timestamp nếu có bất kỳ thay đổi nào hoặc cố ý xóa
            personalInfoMap["profileLastUpdated"] = FieldValue.serverTimestamp()
        } else {
            _isSavingPersonalInfo.value = false
            // _errorMessage.value = "Không có thông tin để lưu." // Tùy chọn
            return
        }

        db.collection("users").document(userId)
            .set(personalInfoMap, SetOptions.merge())
            .addOnSuccessListener {
                _isSavingPersonalInfo.value = false
                _personalInfoSaveSuccess.value = true
                // Cập nhật local state sau khi lưu thành công
                val currentProfile = _fetchedUserInfo.value
                _fetchedUserInfo.value = currentProfile?.copy(
                    name = if (name.isNullOrBlank()) null else name,
                    job = if (job.isNullOrBlank()) null else job,
                    interest = if (interest.isNullOrBlank()) null else interest,
                    otherInfo = if (otherInfo.isNullOrBlank()) null else otherInfo
                )
            }
            .addOnFailureListener { e ->
                _isSavingPersonalInfo.value = false
                _errorMessage.value = "Lỗi khi lưu thông tin cá nhân: ${e.message}"
            }
    }

    // --- Hàm MỚI để lưu AI Tone ---
    fun saveAiChatTone(tone: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _errorMessage.value = "Người dùng chưa đăng nhập."
            return
        }

        _isSavingAiTone.value = true
        _aiToneSaveSuccess.value = false
        val finalTone = tone.ifBlank { "trung lập và thân thiện" }

        val toneData = hashMapOf(
            "aiChatTone" to finalTone,
            "aiChatToneLastUpdated" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(userId)
            .set(toneData, SetOptions.merge())
            .addOnSuccessListener {
                _isSavingAiTone.value = false
                _aiToneSaveSuccess.value = true
                // Cập nhật local state
                val currentProfile = _fetchedUserInfo.value
                _fetchedUserInfo.value = currentProfile?.copy(aiChatTone = finalTone)
                Log.d("NotificationsViewModel", "AI Tone saved: $finalTone")
            }
            .addOnFailureListener { e ->
                _isSavingAiTone.value = false
                _errorMessage.value = "Lỗi khi lưu phong cách AI: ${e.message}"
            }
    }

    // --- Các hàm xử lý event (giữ lại clearErrorMessage, thêm event cho AI Tone) ---
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    fun eventPersonalInfoSaveSuccessShown() { // Đổi tên từ eventSaveSuccessShown
        _personalInfoSaveSuccess.value = false
    }
    fun eventAiToneSaveSuccessShown() { // Hàm mới
        _aiToneSaveSuccess.value = false
    }
}
