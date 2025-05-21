package com.example.lingobuddypck.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kiểm tra nếu người dùng đã đăng nhập, điều hướng phù hợp
        val intent = if (FirebaseAuth.getInstance().currentUser != null) {
            Intent(this, NavigationActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }

        startActivity(intent)
        finish() // Đóng MainActivity vì nó không còn cần thiết
    }
}