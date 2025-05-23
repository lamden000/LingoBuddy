package com.example.lingobuddypck.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.lingobuddypck.R
import com.example.lingobuddypck.ViewModel.LoginViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: LoginViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var googleSignInButton: ImageView
    private lateinit var registerTextView: TextView
    private lateinit var forgotPasswordText: TextView
    private lateinit var rememberMeCheckBox: CheckBox // Declare CheckBox

    private val PREFS_NAME = "LoginPrefs"
    private val PREF_REMEMBER_ME = "rememberMe"

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            viewModel.loginWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Log.w("GoogleSignIn", "Google sign in failed", e)
            Toast.makeText(this, "Đăng nhập bằng Google thất bại!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        // Check if user should be remembered and is already logged in
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shouldRememberUser = sharedPreferences.getBoolean(PREF_REMEMBER_ME, false)

        if (auth.currentUser != null && shouldRememberUser) {
            // User is logged in and chose to be remembered
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return // Skip the rest of onCreate
        } else if (auth.currentUser != null && !shouldRememberUser) {
            auth.signOut()
            sharedPreferences.edit().putBoolean(PREF_REMEMBER_ME, false).apply()
        }
        // If no user or user was signed out, proceed to show login screen

        setContentView(R.layout.activity_login)

        // Init UI
        emailEditText = findViewById(R.id.etEmail)
        passwordEditText = findViewById(R.id.etPassword)
        loginButton = findViewById(R.id.btnLogin)
        googleSignInButton = findViewById(R.id.imageGoogle)
        registerTextView = findViewById(R.id.tvRegister)
        forgotPasswordText = findViewById(R.id.tvForgotPassword)
        rememberMeCheckBox = findViewById(R.id.cbRememberMe) // Initialize CheckBox


        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("787690888171-oi98lg6f89rsc38c1iu4ojuhbutq9ark.apps.googleusercontent.com") // Replace with your actual client ID
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            viewModel.loginWithEmail(email, password)
        }

        googleSignInButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        forgotPasswordText.setOnClickListener {
            val intent = Intent(this, ResetPasswordActivity::class.java)
            startActivity(intent)
        }
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.loginResult.observe(this) { success ->
            if (success) {
                handleSuccessfulLogin()
            }
        }

        viewModel.googleLoginResult.observe(this) { success ->
            if (success) {
                handleSuccessfulLogin()
            }
        }

        viewModel.emailVerificationRequired.observe(this) {
            Toast.makeText(this, "Vui lòng xác thực email trước khi đăng nhập!", Toast.LENGTH_LONG).show()
        }

        viewModel.errorMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSuccessfulLogin() {
        // Save "Remember Me" preference
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean(PREF_REMEMBER_ME, rememberMeCheckBox.isChecked)
            apply()
        }

        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, NavigationActivity::class.java))
        finish()
    }
}