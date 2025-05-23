package com.example.lingobuddypck.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch

data class UserProfileBundle( // Đặt tên cho phù hợp, ví dụ: UserDisplayProfile
    val name: String? = null,
    val job: String? = null,
    val interest: String? = null,
    val otherInfo: String? = null,
    val aiChatTone: String? = null // Thêm trường này
    // Thêm các trường khác nếu fetchCurrentUserInfo() của bạn lấy nhiều hơn
)