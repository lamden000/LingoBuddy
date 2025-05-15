package com.example.lingobuddypck.ViewModel.Repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseWordRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun saveWord(word: String, note: String, onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        val userId = auth.currentUser?.uid ?: return

        val wordData = mapOf(
            "word" to word,
            "note" to note,
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(userId)
            .collection("saved_words")
            .add(wordData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}