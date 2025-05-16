package com.example.lingobuddypck.ui.dashboard
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.R
import com.example.lingobuddypck.ViewModel.Repository.SavedWord
import com.example.lingobuddypck.adapter.SavedWordsAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder // Hoặc AlertDialog thông thường
class SavedWordsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewNoWords: TextView
    private lateinit var wordsAdapter: SavedWordsAdapter

    private val viewModel: SavedWordsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_saved_words, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewSavedWords)
        progressBar = view.findViewById(R.id.progressBarSavedWords)
        textViewNoWords = view.findViewById(R.id.textViewNoWords)

        setupRecyclerView()
        observeViewModel()

        return view
    }

    private fun setupRecyclerView() {
        wordsAdapter = SavedWordsAdapter(
            onEditClick = { savedWord ->
                showEditWordDialog(savedWord)
            },
            onDeleteClick = { savedWord ->
                showDeleteConfirmationDialog(savedWord)
            }
        )
        recyclerView.apply {
            adapter = wordsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.savedWords.observe(viewLifecycleOwner) { words ->
            if (words.isNullOrEmpty()) {
                textViewNoWords.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                textViewNoWords.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                wordsAdapter.submitList(words)
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { errorMessage ->
                if (errorMessage != null) {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
        viewModel.operationResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmationDialog(savedWord: SavedWord) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Word")
            .setMessage("Are you sure you want to delete '${savedWord.word}'?")
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Delete") { dialog, _ ->
                viewModel.deleteWord(savedWord.id)
                dialog.dismiss()
            }
            .show()
    }

    private fun showEditWordDialog(savedWord: SavedWord) {
        // Tạo layout cho dialog (ví dụ: dialog_edit_word.xml)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_word, null)
        val editTextWord = dialogView.findViewById<EditText>(R.id.editTextDialogWord) // Cần có ID này trong dialog_edit_word.xml
        val editTextNote = dialogView.findViewById<EditText>(R.id.editTextDialogNote) // Cần có ID này trong dialog_edit_word.xml

        editTextWord.setText(savedWord.word)
        editTextNote.setText(savedWord.note)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Word")
            .setView(dialogView)
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Save") { dialog, _ ->
                val updatedWord = editTextWord.text.toString().trim()
                val updatedNote = editTextNote.text.toString().trim()
                viewModel.updateWord(savedWord.id, updatedWord, updatedNote)
                dialog.dismiss()
            }
            .show()
    }

}