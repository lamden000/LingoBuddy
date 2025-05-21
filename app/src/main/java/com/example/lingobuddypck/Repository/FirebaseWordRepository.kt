package com.example.lingobuddypck.Repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

import com.google.firebase.firestore.Query // Thêm import này

// Nên tạo một data class để đại diện cho dữ liệu từ Firestore
data class SavedWord(
    val id: String = "", // Để lưu ID của document, cần thiết cho việc sửa/xóa
    val word: String = "",
    val note: String = "",
    val timestamp: com.google.firebase.Timestamp? = null // Hoặc Long nếu bạn lưu timestamp dạng Long
)

class FirebaseWordRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val usersCollection = db.collection("users")

    fun saveWord(word: String, note: String, onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        val userId = auth.currentUser?.uid ?: run {
            onFailure(Exception("User not logged in."))
            return
        }

        val wordData = mapOf(
            "word" to word,
            "note" to note,
            "timestamp" to FieldValue.serverTimestamp()
        )

        usersCollection
            .document(userId)
            .collection("saved_words")
            .add(wordData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    // Hàm để lấy tất cả các từ đã lưu của người dùng hiện tại
    // Sử dụng addSnapshotListener để cập nhật real-time
    fun getSavedWords(
        onResult: (Result<List<SavedWord>>) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: run {
            onResult(Result.failure(Exception("User not logged in.")))
            return
        }

        usersCollection
            .document(userId)
            .collection("saved_words")
            .orderBy("timestamp", Query.Direction.DESCENDING) // Sắp xếp theo thời gian, mới nhất lên đầu
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    onResult(Result.failure(e))
                    return@addSnapshotListener
                }

                val wordList = mutableListOf<SavedWord>()
                snapshots?.forEach { document ->
                    // Chuyển đổi document thành đối tượng SavedWord và gán id
                    val savedWord = document.toObject(SavedWord::class.java)?.copy(id = document.id)
                    savedWord?.let { wordList.add(it) }
                }
                onResult(Result.success(wordList))
            }
    }

    // Hàm để cập nhật một từ
    fun updateWord(
        wordId: String,
        newWord: String,
        newNote: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: run {
            onFailure(Exception("User not logged in."))
            return
        }

        usersCollection
            .document(userId)
            .collection("saved_words")
            .document(wordId)
            .update(mapOf(
                "word" to newWord,
                "note" to newNote
                // Bạn có thể muốn cập nhật cả timestamp nếu cần
                // "timestamp" to FieldValue.serverTimestamp()
            ))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    // Hàm để xóa một từ
    fun deleteWord(
        wordId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: run {
            onFailure(Exception("User not logged in."))
            return
        }

        usersCollection
            .document(userId)
            .collection("saved_words")
            .document(wordId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}