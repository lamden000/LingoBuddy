package com.example.lingobuddypck.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class RegisterViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _registerSuccess = MutableLiveData<Boolean>()
    val registerSuccess: LiveData<Boolean> = _registerSuccess

    private val _emailSent = MutableLiveData<Boolean>()
    val emailSent: LiveData<Boolean> = _emailSent

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    fun register(email: String, password: String, repeatPassword: String) {
        if (email.isBlank() || password.isBlank() || repeatPassword.isBlank()) {
            _errorMessage.value = "Vui lòng nhập đầy đủ Email và Mật khẩu!"
            return
        }

        if (password != repeatPassword) {
            _errorMessage.value = "Mật khẩu nhập lại không khớp!"
            return
        }

        if (password.length < 6) {
            _errorMessage.value = "Mật khẩu phải có ít nhất 6 ký tự!"
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener { verifyTask ->
                            if (verifyTask.isSuccessful) {
                                _registerSuccess.value = true
                                _emailSent.value = true
                            } else {
                                _errorMessage.value = "Không thể gửi email xác thực: ${verifyTask.exception?.message}"
                            }
                        }
                } else {
                    _errorMessage.value = "Lỗi: ${task.exception?.message}"
                }
            }
    }
}
