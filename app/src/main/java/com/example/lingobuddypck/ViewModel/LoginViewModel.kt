package com.example.lingobuddypck.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _loginResult = MutableLiveData<Boolean>()
    val loginResult: LiveData<Boolean> = _loginResult

    private val _emailVerificationRequired = MutableLiveData<Boolean>()
    val emailVerificationRequired: LiveData<Boolean> = _emailVerificationRequired

    private val _googleLoginResult = MutableLiveData<Boolean>()
    val googleLoginResult: LiveData<Boolean> = _googleLoginResult

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    fun loginWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Vui lòng nhập Email và Mật khẩu!"
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        _loginResult.value = true
                    } else {
                        _emailVerificationRequired.value = true
                        auth.signOut()
                    }
                } else {
                    _errorMessage.value = "Sai tài khoản hoặc mật khẩu"
                }
            }
    }

    fun loginWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _googleLoginResult.value = true
                } else {
                    _errorMessage.value = "Lỗi khi xác thực với Firebase!"
                }
            }
    }
}