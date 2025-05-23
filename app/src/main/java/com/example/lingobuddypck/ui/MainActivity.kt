package com.example.lingobuddypck.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth


class MainActivity : AppCompatActivity() {

    private val PREFS_NAME = "LoginPrefs" // Must be the SAME as in LoginActivity
    private val PREF_REMEMBER_ME = "rememberMe" // Must be the SAME as in LoginActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shouldRememberUser = sharedPreferences.getBoolean(PREF_REMEMBER_ME, false)

        val targetIntent: Intent

        if (currentUser != null) {
            if (shouldRememberUser) {
                // User is logged in AND "Remember Me" was checked
                targetIntent = Intent(this, NavigationActivity::class.java)
            } else {
                // User is logged in BUT "Remember Me" was NOT checked (or flag is missing)
                // So, sign them out and send to LoginActivity
                auth.signOut()
                // It's also good practice to ensure the flag is cleared if signing out due to it.
                // sharedPreferences.edit().putBoolean(PREF_REMEMBER_ME, false).apply() // Optional: redundant if LoginActivity handles it on new login
                targetIntent = Intent(this, LoginActivity::class.java)
            }
        } else {
            // No user is logged in
            targetIntent = Intent(this, LoginActivity::class.java)
        }

        startActivity(targetIntent)
        finish() // Đóng MainActivity vì nó không còn cần thiết
    }
}