package com.example.lingobuddypck

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.lingobuddypck.ViewModel.RegisterViewModel
import com.google.firebase.auth.FirebaseAuth

class RegisterActivity : AppCompatActivity() {

    private lateinit var viewModel: RegisterViewModel

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var repeatPasswordEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var signInTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        viewModel = ViewModelProvider(this)[RegisterViewModel::class.java]

        emailEditText = findViewById(R.id.editTextEmail)
        passwordEditText = findViewById(R.id.editTextPassword)
        repeatPasswordEditText = findViewById(R.id.editTextRepeatPassword)
        registerButton = findViewById(R.id.buttonRegister)
        signInTextView = findViewById(R.id.tvSignIn)

        signInTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val repeatPassword = repeatPasswordEditText.text.toString().trim()

            viewModel.register(email, password, repeatPassword)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.registerSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(
                    this,
                    "Đăng ký thành công! Vui lòng xác thực email trước khi đăng nhập.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}

