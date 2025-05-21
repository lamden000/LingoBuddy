package com.example.lingobuddypck.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lingobuddypck.R
import com.google.firebase.auth.FirebaseAuth

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var resetButton: Button
    private lateinit var auth: FirebaseAuth
    private var isCooldownActive = false
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        emailInput = findViewById(R.id.emailInput)
        resetButton = findViewById(R.id.resetButton)
        auth = FirebaseAuth.getInstance()

        resetButton.setOnClickListener {
            val email = emailInput.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Email cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isCooldownActive) {
                Toast.makeText(this, "Please wait before resending", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Reset email sent. Check your inbox.", Toast.LENGTH_LONG).show()
                        startCooldown()
                    } else {
                        val error = task.exception?.localizedMessage ?: "Unknown error"
                        Toast.makeText(this, "Failed: $error", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun startCooldown() {
        isCooldownActive = true
        resetButton.isEnabled = false

        countDownTimer?.cancel() // cancel any existing timer
        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                resetButton.text = "Wait ($seconds)"
            }

            override fun onFinish() {
                isCooldownActive = false
                resetButton.isEnabled = true
                resetButton.text = "Send reset email"
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel() // clean up to prevent leaks
    }
}