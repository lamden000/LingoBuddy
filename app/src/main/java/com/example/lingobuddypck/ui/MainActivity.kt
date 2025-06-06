package com.example.lingobuddypck.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions


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
                auth.signOut()
                targetIntent = Intent(this, LoginActivity::class.java)
            }
        } else {
            // No user is logged in
            targetIntent = Intent(this, LoginActivity::class.java)
        }
        val remoteModelManager = RemoteModelManager.getInstance()
        val model = TranslateRemoteModel.Builder(TranslateLanguage.VIETNAMESE).build()
        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.VIETNAMESE)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )

        remoteModelManager.isModelDownloaded(model)
            .addOnSuccessListener { isDownloaded ->
                if (!isDownloaded) {
                    Log.d("MLKit", "Vietnamese model NOT downloaded. Triggering download.")
                    translator.downloadModelIfNeeded()
                } else {
                    Log.d("MLKit", "Vietnamese model already downloaded.")
                }
            }
            .addOnFailureListener {
                Log.e("MLKit", "Failed to check if model is downloaded.", it)
            }
        preloadMLKitModels()
        startActivity(targetIntent)
        finish()
    }
    fun preloadMLKitModels() {
        val manager = RemoteModelManager.getInstance()

        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        val modelsToDownload = listOf(
            TranslateRemoteModel.Builder(TranslateLanguage.VIETNAMESE).build(),
            TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
        )

        modelsToDownload.forEach { model ->
            val lang = model.language
            manager.isModelDownloaded(model)
                .addOnSuccessListener { isDownloaded ->
                    if (!isDownloaded) {
                        manager.download(model, conditions)
                            .addOnSuccessListener { Log.d("MLKit_Preload", "Successfully downloaded '$lang' model.") }
                            .addOnFailureListener { e -> Log.e("MLKit_Preload", "Failed to download '$lang' model.", e) }
                    } else {
                        Log.d("MLKit_Preload", "Translation model for '$lang' is already downloaded.")
                    }
                }
        }


        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage("")
            .addOnSuccessListener {
                Log.d("MLKit_Preload", "Language ID model is ready.")
            }
            .addOnFailureListener { e ->
                // This will fail if the model couldn't be downloaded.
                Log.e("MLKit_Preload", "Language ID model check/download failed.", e)
            }
    }

}